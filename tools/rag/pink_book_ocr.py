"""
OCR the Thai "สมุดบันทึกสุขภาพแม่และเด็ก" (Mother & Child Health Book, Department of Health / NHSO)
with Typhoon OCR on the Spark gateway, and add its guidance text to the chat AI's RAG corpus
(public.research_chunks, through the research-rag edge function).

Two steps on purpose: parents get health answers from this corpus, so OCR output is read by a
person before the AI is allowed to use it.

  python pink_book_ocr.py ocr [--pages 1-5]   PDF pages -> Typhoon OCR -> out/pages/*.md, out/chunks.jsonl
  python pink_book_ocr.py redo-cut            re-read pages that hit the output limit, as two half-page crops
  python pink_book_ocr.py chunks              re-clean saved pages into out/chunks.jsonl (no network);
                                              OCR misreads are repaired from the PDF's text layer here
  (read out/chunks.jsonl, delete any line that is wrong)
  python pink_book_ocr.py ingest [--force]     out/chunks.jsonl -> research-rag seed_batch
  python pink_book_ocr.py selftest             checks the cleaning and chunking, no network

The gateway key comes from SPARK_LLM_KEY or a hidden prompt. It is never written or printed.
If research-rag has been locked down, set RAG_ADMIN_SECRET the same way.
"""
import base64
import difflib
import getpass
import html
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

PDF = Path(os.environ.get("PINK_BOOK_PDF", r"D:\120.NongRak\Source\Mother-Health-Form.pdf"))
OUT = Path(__file__).with_name("out")
GATEWAY = os.environ.get("SPARK_LLM_URL", "https://spark-4a46.tail047979.ts.net").rstrip("/")
OCR_MODEL = os.environ.get("SPARK_MODEL_OCR", "typhoon-ocr")
RAG_URL = "https://rdylsezwjdujvllyjhqb.supabase.co/functions/v1/research-rag"
BOOK = "สมุดบันทึกสุขภาพแม่และเด็ก (กรมอนามัย / สปสช.)"
CATEGORY = "mother_child_book"

# typhoon-ocr runs with --max-model-len 4096: a ~1400 px page plus 1800 output tokens fits.
# ponytail: pages cut off (finish_reason=length) are listed after the run; if there are many,
# raise the server's --max-model-len and these two numbers together.
LONG_SIDE_PX = 1400
MAX_TOKENS = 1800
# Half-page crops for pages that ran out of room: ~1660 image tokens + 1800 output fits the window.
HALF_LONG_SIDE_PX = 1300

CHUNK_CHARS = 900
# Less real text than this and a chunk is an empty form (lines, boxes, "......"), not knowledge.
MIN_LETTERS = 120
# A line shorter than this is a form label ("ชื่อ-นามสกุล", "มารดา") rather than a sentence.
# Table rows (" | ") are judged separately: a vaccine-schedule row is short but is knowledge.
MIN_LINE_LETTERS = 20
BATCH = 20

# The coloured section tabs printed down the edge of every page. OCR reads them on each page, and
# left in they would sit in every chunk and match every search.
TAB_LABELS = (
    "ข้อมูลแม่และเด็ก", "ข้อมูลฝากครรภ์", "บันทึกการตรวจครรภ์", "แบบประเมินระหว่าง",
    "ความรู้สุขภาพ", "ความรู้การปฏิบัติ", "ประวัติสุขภาพเด็ก", "การเจริญเติบโต", "พัฒนาการเด็ก",
    "บันทึกการตรวจฟัน",
)

# Pages kept out of RAG after reading every chunk (2026-09-13). OCR on these misread numbers and words
# badly enough to mislead a parent, and the text layer could not repair them:
#   1          cover (logos, tab names)
#   13, 20, 21 record tables, no advice
#   23, 24, 25 nutrition flags: wrong serving counts ("เนื้อสัตว์ 15 12 12 ช้อนกินข้าว")
#   38         "ให้ยาลดไข้ ร่วมกับเชื้อด้วงไข้" (the same advice reads correctly on page 40)
#   58, 59     infant / 2-5 y nutrition flags: "กินนมแปดเนื้อถึง 2 ปี", "ลงโทษอาหาร"
#   62         DSPM milestone table: "ไม่ 5 ฟุต กินไม่ 2 ฟุต เหล็กก็ไม่หล่อ"
SKIP_PAGES = {1, 13, 20, 21, 23, 24, 25, 38, 58, 59, 62}

# ------------------------------------------------------------------------------------------------
# Repairing OCR misreads with the PDF's own text layer
#
# The layer has the right letters, but its Thai font drops the combining marks ("สรางเสริมภูมิคุมกัน");
# OCR has every mark but misreads some words ("ช่วยส่งต่อกฎมีดแก้ไขได้" for "…ภูมิคุ้มกันโรค").
# Aligning the two on base letters (marks and spaces ignored) keeps OCR where they agree and takes the
# layer's letters where they do not. Done in code: a Typhoon 2 rewrite of whole pages was tried first
# and copied the OCR back, misreads included.
# ------------------------------------------------------------------------------------------------

THAI_MARKS = re.compile(r"[ัิ-ฺ็-๎]")
# Share of a line's base letters that must be found in the layer before it is trusted to fix it.
REPAIR_MATCH = 0.6
# A misread word is replaced only if it is at most this many base letters on both sides...
REPAIR_MAX_SPAN = 16
# ...and sits between agreeing runs of at least this many letters.
REPAIR_ANCHOR = 4


def normalize_layer(text: str) -> str:
    """The PDF's font writes sara am as nikhahit + sara aa ("แนะนํา", "นํ้า"); one character is what OCR has."""
    return text.replace("ํ้า", "้ำ").replace("ํา", "ำ")


# Misreads that survived both OCR and the text-layer repair, each checked against the PDF's own text.
# Exact strings, applied last.
MANUAL_FIXES = {
    "ช่วยส่งต่อกฎมีดแก้ไขได้ไปยังทารกได้": "ช่วยส่งต่อภูมิคุ้มกันโรคไปยังทารกได้",  # p.7
    "ทารกชวบปีแรก": "ทารกขวบปีแรก",  # p.7
    "กกรเฉลิมดิษฐิโต๊ดีคีคอลวงนั้นหนักอยู่ในระดับนี้": "การเจริญเติบโตดี ควรคงน้ำหนักอยู่ในระดับนี้",  # p.45
    "-1 5 ถึง < 1 5 SD": "-1.5 ถึง < 1.5 SD",  # p.45: OCR lost the decimal points
    "-2 ถึง < -1 5 SD": "-2 ถึง < -1.5 SD",
    "1 5 ถึง 2 SD": "1.5 ถึง 2 SD",
    "น้าหนัก": "น้ำหนัก",
    "ตองดูกราฟ": "ต้องดูกราฟ",
    "สวนสูง": "ส่วนสูง",
}


def apply_fixes(text: str) -> str:
    text = normalize_layer(text)  # OCR copies the same sara am encoding on some pages
    for wrong, right in MANUAL_FIXES.items():
        text = text.replace(wrong, right)
    return text


def base_index(text: str) -> tuple:
    """(text without spaces or Thai combining marks, the position in [text] of each remaining char)."""
    chars, positions = [], []
    for i, ch in enumerate(text):
        if ch.isspace() or THAI_MARKS.match(ch):
            continue
        chars.append(ch)
        positions.append(i)
    return "".join(chars), positions


def span(text: str, positions: list, start: int, end: int) -> str:
    """The original text of base chars [start, end), with their marks and the spaces after them."""
    if start >= end:
        return ""
    stop = positions[end] if end < len(positions) else len(text)
    return text[positions[start]:stop]


def repair_line(line: str, layer: str, layer_base: str, layer_positions: list) -> str:
    line_base, line_positions = base_index(line)
    if len(line_base) < 8:
        return line
    matcher = difflib.SequenceMatcher(None, line_base, layer_base, autojunk=False)
    blocks = [b for b in matcher.get_matching_blocks() if b.size >= 3]
    if sum(b.size for b in blocks) < REPAIR_MATCH * len(line_base):
        return line  # this line is not in the text layer (image-only part of the page)

    a_start, a_end = blocks[0].a, blocks[-1].a + blocks[-1].size
    b_start, b_end = blocks[0].b, blocks[-1].b + blocks[-1].size
    ops = difflib.SequenceMatcher(
        None, line_base[a_start:a_end], layer_base[b_start:b_end], autojunk=False
    ).get_opcodes()

    # A one- or two-letter "match" inside a misread word is coincidence ("ม" in กฎมีด / ภูมิ); taking the
    # OCR letters there would splice a wrong vowel into the corrected word, so fold it into the fix.
    merged = []
    for k, (tag, i1, i2, j1, j2) in enumerate(ops):
        if tag == "equal" and i2 - i1 < 3 and 0 < k < len(ops) - 1:
            tag = "replace"
        if tag != "equal" and merged and merged[-1][0] != "equal":
            _, p1, _, q1, _ = merged[-1]
            merged[-1] = ("replace", p1, i2, q1, j2)
        else:
            merged.append((tag, i1, i2, j1, j2))

    out = [line[:line_positions[a_start]]]
    for k, (tag, i1, i2, j1, j2) in enumerate(merged):
        ocr_part = span(line, line_positions, a_start + i1, a_start + i2)
        if tag == "equal":
            out.append(ocr_part)
            continue
        # Only a short, well-anchored disagreement is a misread word. Anything else — text the layer
        # has between two matched pieces, or a long mismatch — would splice unrelated layer text into
        # the line (seen on page 4), so the OCR stays as read.
        before = merged[k - 1] if k > 0 else None
        after = merged[k + 1] if k + 1 < len(merged) else None
        anchored = all(n is not None and n[0] == "equal" and n[2] - n[1] >= REPAIR_ANCHOR for n in (before, after))
        ocr_len, layer_len = i2 - i1, j2 - j1
        small = (
            (0 < ocr_len <= REPAIR_MAX_SPAN and 0 < layer_len <= REPAIR_MAX_SPAN)  # misread word
            or (ocr_len == 0 and layer_len <= 2)   # OCR dropped a letter ("ลง" for "ลอง")
            or (layer_len == 0 and ocr_len <= 2)   # OCR added a stray letter
        )
        if anchored and small:
            out.append(span(layer, layer_positions, b_start + j1, b_start + j2).replace("\n", " "))
        else:
            out.append(ocr_part)
    if a_end < len(line_base):
        out.append(line[line_positions[a_end]:])
    return re.sub(r"[ \t]+", " ", "".join(out)).strip()


def repair_text(text: str, layer: str) -> str:
    layer_base, layer_positions = base_index(layer)
    return "\n".join(repair_line(line, layer, layer_base, layer_positions) for line in text.split("\n"))

OCR_PROMPT = """Extract all text from the image.
Instructions:
- Only return the clean Markdown.
- Do not include any explanation or extra text.
- You must include all information on the page.
Formatting Rules:
- Tables: Render tables using <table>...</table> in clean HTML format.
- Equations: Render equations using LaTeX syntax with inline ($...$) and block ($$...$$).
- Images/Charts/Diagrams: Wrap any clearly defined visual areas (e.g. charts, diagrams, pictures) in:
<figure>
Describe the image's main elements (people, objects, text), note any contextual clues (place, event, culture), mention visible text and its meaning, provide deeper analysis when relevant (especially for financial charts, graphs, or documents with numbers), and explain the significance of the image if applicable.
</figure>
- Page Numbers: Wrap page numbers in <page_number>...</page_number> (e.g., <page_number>14</page_number>).
- Checkboxes: Use ☐ for unchecked and ☑ for checked boxes."""


# --------------------------------------------------------------------------------------------
# Cleaning and chunking (pure, covered by selftest)
# --------------------------------------------------------------------------------------------

def letters(text: str) -> int:
    """Thai and Latin letters only: dots, lines, digits and box characters are not content."""
    return sum(1 for ch in text if "\u0e00" <= ch <= "\u0e7f" or ch.isascii() and ch.isalpha())


def is_tab_label(line: str) -> bool:
    """OCR reads the tabs with stray spaces and small misreadings, so match loosely on the start."""
    squeezed = line.replace(" ", "")
    return any(squeezed.startswith(label) and len(squeezed) <= len(label) + 25 for label in TAB_LABELS)


def table_text(match) -> str:
    """An OCR'd table as " | " rows — or nothing, when it is a blank form waiting to be filled in.

    A form's rows are mostly empty cells; a reference table (a vaccine schedule) is filled in.
    """
    rows = []
    for row_html in re.findall(r"<tr.*?</tr>", match.group(0), flags=re.S):
        cells = re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", row_html, flags=re.S)
        rows.append([re.sub(r"\s+", " ", re.sub(r"<[^>]+>|[.…_☐☑�]+", " ", c)).strip() for c in cells])
    rows = [r for r in rows if r]
    if not rows:
        return "\n"
    header = rows[0]
    body = rows[1:] or rows

    def empty(cell: str) -> bool:
        # The Typhoon 2 merge sometimes "fills" a blank form by repeating its column headers.
        squeezed = cell.replace(" ", "")
        return not re.search(r"\w", cell) or any(squeezed and squeezed == h.replace(" ", "") for h in header)

    blank = sum(1 for r in body if any(empty(c) for c in r[1:] or r))
    if blank * 2 >= len(body):
        return "\n"
    return "\n" + "\n".join(" | ".join(r) for r in rows) + "\n"


def clean(markdown: str) -> str:
    """OCR markdown -> plain text fit for retrieval.

    Figure descriptions are the model's own words about a picture, not the book's text, so they
    are dropped rather than handed to the chat AI as reference knowledge.
    """
    text = re.sub(r"<figure>.*?</figure>", " ", markdown, flags=re.S)
    text = re.sub(r"<page_number>.*?</page_number>", " ", text, flags=re.S)
    text = re.sub(r"<table.*?</table>", table_text, text, flags=re.S)
    # A line with a tick box or a dotted blank is a field to fill in ("☐ เบาหวาน ชนิดที่ ......"),
    # not advice. Tables were converted above, so their filled cells are not caught here.
    text = "\n".join(line for line in text.split("\n") if not re.search(r"[☐☑]|[.…_]{3,}", line))
    text = re.sub(r"<[^>]+>", " ", text)
    text = html.unescape(text)  # after the tag strip, so "&lt; -2 SD" cannot be taken for a tag
    text = re.sub(r"[.…_]{3,}", " ", text)             # fill-in blanks
    text = re.sub(r"[☐☑�]", " ", text)                  # boxes, and the broken tail OCR leaves on long box runs
    text = re.sub(r"[ \t]+", " ", text)

    kept_lines = []
    for line in text.split("\n"):
        heading = re.match(r"^\s*#+\s*", line) is not None
        stripped = re.sub(r"^\s*#+\s*", "", line).strip(" |")
        if not stripped:
            kept_lines.append("")  # paragraph breaks survive
        elif heading:
            kept_lines.append(stripped)  # section names are short but say what the text is about
        elif is_tab_label(stripped):
            continue
        elif " | " in line and letters(stripped) >= 6:
            kept_lines.append(line.strip())
        elif letters(stripped) >= MIN_LINE_LETTERS:
            kept_lines.append(stripped)
    text = "\n".join(kept_lines)
    text = re.sub(r"\n\s*\n\s*\n+", "\n\n", text)
    return text.strip()


def chunk(text: str, size: int = CHUNK_CHARS) -> list:
    """Paragraph-first chunks of at most [size] chars; an oversize paragraph is split on spaces."""
    pieces = []
    for para in (p.strip() for p in text.split("\n\n")):
        if not para:
            continue
        while len(para) > size:
            cut = para.rfind(" ", 0, size)
            cut = cut if cut > size // 2 else size
            pieces.append(para[:cut].strip())
            para = para[cut:].strip()
        pieces.append(para)

    chunks, current = [], ""
    for piece in pieces:
        if current and len(current) + 2 + len(piece) > size:
            chunks.append(current)
            current = piece
        else:
            current = f"{current}\n\n{piece}" if current else piece
    if current:
        chunks.append(current)
    return chunks


def title_for(text: str, page: int) -> str:
    """The first short line of real text names the chunk; the page number keeps it findable."""
    for line in text.splitlines():
        line = line.strip(" |")
        if 4 <= letters(line) and len(line) <= 70:
            return f"สมุดบันทึกสุขภาพแม่และเด็ก: {line} (หน้า {page})"
    return f"สมุดบันทึกสุขภาพแม่และเด็ก หน้า {page}"


def page_chunks(markdown: str, page: int, layer: str = "") -> tuple:
    """(kept chunks, number dropped as empty form). [layer] is the PDF's own text for the page, if any."""
    text = clean(markdown)
    if letters(layer) >= 50:
        text = repair_text(text, layer)
    text = apply_fixes(text)
    kept, dropped = [], 0
    for part in chunk(text):
        if letters(part) < MIN_LETTERS:
            dropped += 1
            continue
        kept.append({
            "title": title_for(part, page),
            "source": f"{BOOK} หน้า {page}",
            "category": CATEGORY,
            "content": part,
        })
    return kept, dropped


# --------------------------------------------------------------------------------------------
# Network
# --------------------------------------------------------------------------------------------

def post_json(url: str, body: dict, headers: dict, timeout: int) -> dict:
    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", **headers},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        # Name what came back: "401" alone does not say whether the key or the path is wrong.
        detail = e.read().decode("utf-8", "replace")[:300]
        raise RuntimeError(f"{url} -> HTTP {e.code}: {detail}") from None


def secret(env_name: str, prompt: str, required: bool) -> str:
    value = os.environ.get(env_name, "").strip()
    if not value and required:
        value = getpass.getpass(prompt).strip()
    if required and not value:
        sys.exit(f"{env_name} is required")
    return value


def render_page(doc, index: int) -> str:
    import fitz  # local import: selftest needs no PyMuPDF
    page = doc[index]
    zoom = LONG_SIDE_PX / max(page.rect.width, page.rect.height)
    pixmap = page.get_pixmap(matrix=fitz.Matrix(zoom, zoom))
    return base64.b64encode(pixmap.tobytes("png")).decode("ascii")


def render_region(doc, index: int, top: float, bottom: float) -> str:
    """A horizontal band of the page (fractions of its height), rendered sharper than a full page."""
    import fitz
    page = doc[index]
    r = page.rect
    clip = fitz.Rect(r.x0, r.y0 + r.height * top, r.x1, r.y0 + r.height * bottom)
    zoom = HALF_LONG_SIDE_PX / max(clip.width, clip.height)
    pixmap = page.get_pixmap(matrix=fitz.Matrix(zoom, zoom), clip=clip)
    return base64.b64encode(pixmap.tobytes("png")).decode("ascii")


def gateway_key() -> str:
    if os.environ.get("OCR_RELAY_URL", "").strip():
        return secret("OCR_RELAY_TOKEN", "OCR relay token (hidden): ", required=True)
    return secret("SPARK_LLM_KEY", "Spark gateway key (hidden): ", required=True)


def call_gateway(key: str, body: dict) -> dict:
    relay = os.environ.get("OCR_RELAY_URL", "").strip()
    if relay:
        # Temporary Supabase function that holds the gateway key server-side; [key] is its
        # one-time token instead of the gateway key.
        return post_json(relay, body, {"x-ocr-token": key}, 300)
    return post_json(f"{GATEWAY}/v1/chat/completions", body, {"Authorization": f"Bearer {key}"}, 300)


def ocr_page(key: str, image_b64: str) -> tuple:
    body = {
        "model": OCR_MODEL,
        "messages": [{
            "role": "user",
            "content": [
                {"type": "text", "text": OCR_PROMPT},
                {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{image_b64}"}},
            ],
        }],
        "max_tokens": MAX_TOKENS,
        "temperature": 0.1,
    }
    data = call_gateway(key, body)
    choice = (data.get("choices") or [{}])[0]
    text = (choice.get("message") or {}).get("content") or ""
    return text, choice.get("finish_reason", "?")


# --------------------------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------------------------

def page_range(argv: list, total: int) -> range:
    if "--pages" in argv:
        first, _, last = argv[argv.index("--pages") + 1].partition("-")
        return range(int(first), int(last or first) + 1)
    return range(1, total + 1)


def cmd_ocr(argv: list) -> None:
    import fitz
    key = gateway_key()
    pages_dir = OUT / "pages"
    pages_dir.mkdir(parents=True, exist_ok=True)
    doc = fitz.open(PDF)

    for number in page_range(argv, doc.page_count):
        md_path = pages_dir / f"{number:03d}.md"
        if md_path.exists():
            continue  # resume: finished pages are not paid for twice
        try:
            text, finish = ocr_page(key, render_page(doc, number - 1))
        except Exception as e:
            print(f"page {number}: FAILED - {e}")
            continue
        md_path.write_text(text, encoding="utf-8")
        (pages_dir / f"{number:03d}.json").write_text(json.dumps({"finish_reason": finish}), encoding="utf-8")
        print(f"page {number}: {len(text)} chars, finish={finish}")

    build_chunks(doc.page_count)


def build_chunks(total: int) -> None:
    import fitz
    doc = fitz.open(PDF)
    pages_dir = OUT / "pages"
    rows, dropped, truncated, missing = [], 0, [], []
    for number in range(1, total + 1):
        md_path = pages_dir / f"{number:03d}.md"
        if not md_path.exists():
            missing.append(number)
            continue
        if number in SKIP_PAGES:
            continue
        meta_path = pages_dir / f"{number:03d}.json"
        if meta_path.exists() and json.loads(meta_path.read_text(encoding="utf-8")).get("finish_reason") == "length":
            truncated.append(number)
        layer = normalize_layer(doc[number - 1].get_text())
        kept, gone = page_chunks(md_path.read_text(encoding="utf-8"), number, layer)
        for row in kept:
            # The same box of text printed on several pages is stored once.
            if any(existing["content"] == row["content"] for existing in rows):
                dropped += 1
            else:
                rows.append(row)
        dropped += gone

    with (OUT / "chunks.jsonl").open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"\nchunks kept: {len(rows)}   dropped as empty form: {dropped}")
    print(f"pages not OCR'd yet: {missing or 'none'}")
    print(f"pages cut off (finish=length): {truncated or 'none'}")
    print(f"review {OUT / 'chunks.jsonl'} before running: python {Path(__file__).name} ingest")


def cut_pages(total: int) -> list:
    """Pages whose OCR stopped at the output limit, so the bottom of the page is missing."""
    pages_dir = OUT / "pages"
    cut = []
    for number in range(1, total + 1):
        meta = pages_dir / f"{number:03d}.json"
        if meta.exists() and json.loads(meta.read_text(encoding="utf-8")).get("finish_reason") == "length":
            cut.append(number)
    return cut


def cmd_redo_cut(argv: list) -> None:
    """Re-reads cut-off pages as two overlapping halves, each with the full output budget.

    ponytail: the overlap band can repeat a line or two in the page text; chunks tolerate that.
    """
    import fitz
    key = gateway_key()
    doc = fitz.open(PDF)
    pages_dir = OUT / "pages"
    for number in cut_pages(doc.page_count):
        meta = json.loads((pages_dir / f"{number:03d}.json").read_text(encoding="utf-8"))
        if meta.get("split"):
            print(f"page {number}: already re-read in halves, still at the limit - keeping what was read")
            continue
        # A chart page makes typhoon-ocr repeat axis labels until the limit ("1 ปี, 2 ปี … 236 ปี");
        # nothing of it survives cleaning, and a sharper re-read would only repeat for longer.
        # A record page's OCR can clean down to almost nothing while its lost bottom still holds facts
        # (the OGTT thresholds on page 10), so the PDF's own text decides too.
        ocr_letters = letters(clean((pages_dir / f"{number:03d}.md").read_text(encoding="utf-8")))
        layer_letters = letters(normalize_layer(doc[number - 1].get_text()))
        if ocr_letters < MIN_LETTERS and layer_letters < 100:
            print(f"page {number}: skipped - no real text (chart or repetition), nothing to recover")
            continue
        try:
            top, top_finish = ocr_page(key, render_region(doc, number - 1, 0.0, 0.55))
            bottom, bottom_finish = ocr_page(key, render_region(doc, number - 1, 0.45, 1.0))
        except Exception as e:
            print(f"page {number}: REDO FAILED - {e}")
            continue
        finish = "length" if "length" in (top_finish, bottom_finish) else "stop"
        (pages_dir / f"{number:03d}.md").write_text(f"{top}\n\n{bottom}", encoding="utf-8")
        (pages_dir / f"{number:03d}.json").write_text(json.dumps({"finish_reason": finish, "split": True}), encoding="utf-8")
        print(f"page {number}: split re-read, {len(top) + len(bottom)} chars, finish={finish}")
    build_chunks(doc.page_count)


def cmd_ingest(argv: list) -> None:
    marker = OUT / ".ingested"
    if marker.exists() and "--force" not in argv:
        sys.exit(f"already ingested ({marker.read_text(encoding='utf-8').strip()}); "
                 "research-rag only appends, so a second run duplicates rows. Use --force only after "
                 "deleting the old rows (category = 'mother_child_book').")
    rows = [json.loads(line) for line in (OUT / "chunks.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    if not rows:
        sys.exit("chunks.jsonl is empty - run the ocr step first")

    admin = secret("RAG_ADMIN_SECRET", "", required=False)
    headers = {"x-admin-secret": admin} if admin else {}
    added = 0
    for start in range(0, len(rows), BATCH):
        batch = rows[start:start + BATCH]
        result = post_json(RAG_URL, {"action": "seed_batch", "chunks": batch}, headers, 280)
        if result.get("added") != len(batch):
            sys.exit(f"batch at {start}: sent {len(batch)}, research-rag answered {result} - stopping "
                     f"({added} rows already added)")
        added += len(batch)
        print(f"added {added}/{len(rows)}")
    marker.write_text(f"{added} rows", encoding="utf-8")

    check = post_json(RAG_URL, {"action": "search", "query": "ฝากครรภ์ครั้งแรกควรทำอะไร", "k": 3}, headers, 60)
    print("\nsearch check (ฝากครรภ์ครั้งแรกควรทำอะไร):")
    for c in check.get("chunks") or []:
        print(" -", c.get("title"))


def cmd_selftest() -> None:
    form = "<table><tr><td>ครั้งที่</td><td>วันที่</td></tr><tr><td>1</td><td>..........</td></tr></table>"
    advice = ("# การฝากครรภ์\n\nหญิงตั้งครรภ์ควรฝากครรภ์ทันทีที่รู้ว่าตั้งครรภ์ และควรฝากครรภ์อย่างน้อย 8 ครั้ง "
              "เพื่อตรวจสุขภาพแม่และทารกในครรภ์ รับวัคซีน และรับยาเสริมธาตุเหล็กและโฟลิก "
              "หากมีเลือดออกทางช่องคลอด ปวดท้องรุนแรง หรือลูกดิ้นน้อยลง ให้ไปโรงพยาบาลทันที")
    page = f"{advice}\n\n<figure>ภาพแม่อุ้มลูก</figure>\n\n{form}\n\n<page_number>12</page_number>"

    cleaned = clean(page)
    assert "ภาพแม่อุ้มลูก" not in cleaned, "figure descriptions must not reach RAG"
    assert "<" not in cleaned and "....." not in cleaned, "tags and blanks must be stripped"

    kept, dropped = page_chunks(page, 12)
    assert len(kept) == 1, f"advice must be kept as one chunk, got {len(kept)}"
    assert kept[0]["title"] == "สมุดบันทึกสุขภาพแม่และเด็ก: การฝากครรภ์ (หน้า 12)", kept[0]["title"]
    assert kept[0]["source"].endswith("หน้า 12") and kept[0]["category"] == CATEGORY

    only_form, _ = page_chunks(f"{form}\n\n<page_number>3</page_number>", 3)
    assert only_form == [], "an empty form page must add nothing to RAG"

    family_page = ("ข้อมูลแม่และเด็ก\nข้อมูลฝากครรภ์\n# ข้อมูลครอบครัว\nชื่อ-นามสกุล\nมารดา\n"
                   "เลขประจำตัวประชาชน ☐☐☐☐☐☐☐☐�\nอายุ ☐ ปี โทรศัพท์ ☐\n\n"
                   "คุณพ่อคุณแม่ต้องไปแจ้งเกิด ณ อำเภอ เทศบาล หรือเขตต่าง ๆ ซึ่งเป็นท้องที่ที่มีการเกิด "
                   "เพื่อแจ้งเกิดและเพิ่มชื่อในทะเบียนบ้านภายใน 15 วัน")
    family = clean(family_page)
    assert "ข้อมูลฝากครรภ์" not in family, "section tabs must be stripped"
    assert "มารดา" not in family and "�" not in family, "form labels and broken characters must go"
    assert "ภายใน 15 วัน" in family, "the real advice on a form page must stay"

    assert clean("แบบประเมิน ระหว่างตั้งครรภ์และหลังคลอด") == "", "loosely-read tab labels must go"
    assert clean("## พัฒนาการเด็กวัย 9 เดือน") == "พัฒนาการเด็กวัย 9 เดือน", "a heading that starts like a tab stays"

    checklist = clean("## ประวัติการเจ็บป่วยของหญิงตั้งครรภ์\n*   โรคประจำตัว ☐ ไม่มี\n"
                      "*   ☐ ลมชัก ☐ ครรภ์เป็นพิษ ☐ ภาวะพิการแต่กำเนิด ☐ อื่น ๆ ระบุ ............\n"
                      "การให้ข้อมูลเกี่ยวกับการตั้งครรภ์ที่ผ่านมาอย่างครบถ้วนถูกต้อง ช่วยป้องกันความเสี่ยงต่อภาวะแทรกซ้อน")
    assert "ลมชัก" not in checklist and "โรคประจำตัว" not in checklist, f"tick-box fields must go: {checklist!r}"
    assert "ช่วยป้องกันความเสี่ยง" in checklist, "advice next to a checklist must stay"

    echoed = clean("<table><tr><td>ครรภ์</td><td>วิธีคลอด</td><td>น้ำหนัก ทารก</td></tr>"
                   "<tr><td>1</td><td>วิธีคลอด</td><td>น้ำหนักทารก</td></tr>"
                   "<tr><td>2</td><td>วิธีคลอด</td><td>น้ำหนักทารก</td></tr></table>")
    assert echoed == "", f"a form whose cells only repeat the headers is still blank: {echoed!r}"

    ocr_line = "การได้รับวัคซีนในหญิงตั้งครรภ์ ช่วยส่งต่อกฎมีดแก้ไขได้ไปยังทารกได้"
    layer = normalize_layer("หัวข้ออื่น\nการไดรับวัคซีนในหญิงตั้งครรภ ชวยสงตอภูมิคุมกันโรคไปยังทารกได\nแนะนําที่")
    repaired = repair_text(ocr_line, layer)
    assert repaired == "การได้รับวัคซีนในหญิงตั้งครรภ์ ช่วยส่งต่อภูมิคุมกันโรคไปยังทารกได้", repaired
    assert repair_text("บรรทัดนี้ไม่มีอยู่ในข้อความของไฟล์ PDF เลย", layer) == "บรรทัดนี้ไม่มีอยู่ในข้อความของไฟล์ PDF เลย", \
        "a line missing from the text layer must stay as OCR read it"
    assert normalize_layer("แนะนํา") == "แนะนำ"
    assert normalize_layer("นํ้าหนัก") == "น้ำหนัก"
    assert apply_fixes("ตามเกณฑ์ | กกรเฉลิมดิษฐิโต๊ดีคีคอลวงนั้นหนักอยู่ในระดับนี้ | -1 5 ถึง < 1 5 SD") == \
        "ตามเกณฑ์ | การเจริญเติบโตดี ควรคงน้ำหนักอยู่ในระดับนี้ | -1.5 ถึง < 1.5 SD"
    growth = clean("<table><tr><td>น้อย</td><td>ขาดอาหาร</td><td>&lt; -2 SD</td></tr>"
                   "<tr><td>มาก</td><td>อ้วน</td><td>&gt; 2 SD</td></tr></table>")
    assert growth.endswith("มาก | อ้วน | > 2 SD") and "&" not in growth, f"entities must be decoded: {growth!r}"

    misread = repair_text("หากมีข้อสงสัย ไม่ควรใช้รีวิลลงถูกลงผิดด้วยตนเอง ควรปรึกษาแพทย์",
                          normalize_layer("หากมีขอสงสัย ไมควรใชวิธีลองถูกลองผิดดวยตนเอง ควรปรึกษาแพทย"))
    assert "วิธีลองถูกลองผิด" in misread and misread.startswith("หากมีข้อสงสัย ไม่ควรใช้"), misread

    spliced = repair_text("ไม่ควรใช้วิธีลองถูกลองผิดด้วยตนเอง ควรปรึกษาแพทย์",
                          normalize_layer("ไมควรใชวิธีลองถูกลองผิด\nเมื่อเขารวมกิจกรรมครั้งแรก\nดวยตนเอง ควรปรึกษาแพทย"))
    assert "กิจกรรม" not in spliced, f"text from elsewhere in the layer must not be spliced in: {spliced!r}"
    assert spliced == "ไม่ควรใช้วิธีลองถูกลองผิดด้วยตนเอง ควรปรึกษาแพทย์", spliced

    schedule = clean("<table><tr><td>อายุ</td><td>วัคซีน</td></tr><tr><td>2 เดือน</td><td>DTP-HB-Hib 1, OPV1</td></tr></table>")
    assert "2 เดือน | DTP-HB-Hib 1, OPV1" in schedule, f"short table rows are knowledge: {schedule!r}"

    long_text = "\n\n".join(["ข้อความ " * 60] * 4)
    parts = chunk(long_text, 300)
    assert all(len(p) <= 300 for p in parts), "no chunk may exceed the size"
    assert "".join(parts).replace("\n", "").replace(" ", "") == long_text.replace("\n", "").replace(" ", ""), \
        "chunking must not lose text"
    print("selftest ok")


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "ocr":
        cmd_ocr(sys.argv[2:])
    elif command == "redo-cut":
        cmd_redo_cut(sys.argv[2:])
    elif command == "chunks":
        import fitz
        build_chunks(fitz.open(PDF).page_count)  # re-clean saved OCR text without calling Typhoon again
    elif command == "ingest":
        cmd_ingest(sys.argv[2:])
    elif command == "selftest":
        cmd_selftest()
    else:
        sys.exit(__doc__)
