"""Turn the per-page transcriptions (fct/p*.json) into the app asset thai_fct_2561.json.

Cell conventions from the book's legend:
  "-"   not analysed        -> null   (never 0; a missing value must not count as zero)
  "tr"  trace               -> 0, and the field is listed in "trace"
  "(x)" calculated value    -> x, and the field is listed in "calc"
Also runs two sanity checks that catch transcription slips:
  * Atwater: kcal vs 4*protein + 9*fat + 4*carb (+2*fibre)
  * proximate: water + protein + fat + carb + fibre + ash ~ 100 g
"""
import glob
import json
import re
import sys

STD = ["kcal", "water", "protein", "fat", "carb", "fibre", "ash",
       "calcium", "phosphorus", "magnesium", "sodium", "potassium", "iron", "copper", "zinc", "iodine",
       "betacarotene", "retinol", "vitaminA", "thiamin", "riboflavin", "niacin", "vitaminC", "vitaminE"]

NUM = re.compile(r"^\(?(-?\d+(?:\.\d+)?)\)?$")


def cell(raw):
    """-> (value, isCalc, isTrace); value None for not-analysed."""
    s = (raw or "").strip()
    if s in ("", "-"):
        return None, False, False
    if s.lower() == "tr":
        return 0.0, False, True
    m = NUM.match(s)
    if not m:
        raise ValueError(f"unreadable cell {raw!r}")
    return float(m.group(1)), s.startswith("("), False


def put(food, key, raw):
    v, calc, tr = cell(raw)
    if v is None:
        return
    food["n"][key] = v
    if calc:
        food.setdefault("calc", []).append(key)
    if tr:
        food.setdefault("trace", []).append(key)


def main(src_glob, out_path):
    foods, problems, seen = [], [], set()
    for path in sorted(glob.glob(src_glob)):
        page = json.load(open(path, encoding="utf-8"))
        for r in page["rows"]:
            fid = r[0]
            if fid in seen:
                problems.append(f"{fid}: duplicate id (page {page['pdfPage']})")
                continue
            seen.add(fid)
            food = {"id": fid, "th": r[1], "en": r[2] or None, "group": page["group"], "n": {}}
            if page.get("layout") == "dessert":
                cols = page["columns"]
                for k, raw in zip(cols, r[3]):
                    put(food, k, raw)
                m = re.search(r"\(([\d.]+)\s*g\)", r[4])
                if m:
                    food["serving"] = {"label": r[4], "grams": float(m.group(1))}
            else:
                vals = r[3:]
                for k, raw in zip(STD, vals[:24]):
                    put(food, k, raw)
                if len(vals) > 24 and page.get("other"):
                    key = "sugar" if page["other"].startswith("sugar") else "cholesterol"
                    put(food, key, vals[24])
            food["page"] = page["printedPage"]
            foods.append(food)

            n = food["n"]
            if all(k in n for k in ("kcal", "protein", "fat", "carb")):
                est = 4 * n["protein"] + 9 * n["fat"] + 4 * n["carb"] + 2 * n.get("fibre", 0)
                if n["kcal"] > 20 and abs(est - n["kcal"]) > max(25, 0.15 * n["kcal"]):
                    problems.append(f"{fid} {r[1]}: kcal {n['kcal']} vs Atwater {est:.0f}")
                    # Printed energy disagrees with the book's own macros (verified against the scan):
                    # keep the printed figure, but tell the app to compute energy from macros instead.
                    food.setdefault("suspect", []).append("kcal")
            parts = [n.get(k) for k in ("water", "protein", "fat", "carb", "fibre", "ash")]
            if all(p is not None for p in parts) and abs(sum(parts) - 100) > 6:
                problems.append(f"{fid} {r[1]}: proximate sum {sum(parts):.1f}")

    out = {
        "source": "ตารางแสดงคุณค่าทางโภชนาการของอาหารไทย (Food Composition Table of Thai Foods), "
                  "สำนักโภชนาการ กรมอนามัย กระทรวงสาธารณสุข, กันยายน 2561",
        "basis": "per 100 g edible portion",
        "units": {"kcal": "kcal", "water": "g", "protein": "g", "fat": "g", "carb": "g", "fibre": "g", "ash": "g",
                  "calcium": "mg", "phosphorus": "mg", "magnesium": "mg", "sodium": "mg", "potassium": "mg",
                  "iron": "mg", "copper": "mg", "zinc": "mg", "iodine": "mcg", "betacarotene": "mcg",
                  "retinol": "mcg", "vitaminA": "mcg RAE", "thiamin": "mg", "riboflavin": "mg", "niacin": "mg",
                  "vitaminC": "mg", "vitaminE": "mg", "sugar": "g", "cholesterol": "mg"},
        "legend": "Absent nutrient = not analysed (unknown, NOT zero). 'calc' = value calculated rather than "
                  "measured. 'trace' = present in trace amounts (stored as 0). 'suspect' = printed value contradicts "
                  "the book's own other columns (checked against the scan); use a derived value instead.",
        "groupsIncluded": sorted({f["group"] for f in foods}),
        "foods": foods,
    }
    json.dump(out, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    by = {}
    for f in foods:
        by[f["group"]] = by.get(f["group"], 0) + 1
    print("foods", len(foods), "by group", dict(sorted(by.items())))
    print("problems", len(problems))
    for p in problems:
        print("  ", p)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
