"""Generate the starter ingredient pack shipped as an app asset.

Run from the repository root:

    python tools/generate_starter_pack.py

Why a generator instead of a hand-written JSON file: the numbers need to be reviewable in one table, the
Russian and English names must stay side by side, and regenerating has to be a reviewable diff rather
than an edit to a 60-entry blob.

What these numbers are: typical composition figures from public food-composition tables, expressed per
100 g (per 100 ml for liquids). They are ESTIMATES for generic ingredients, not measurements of a specific
product on a specific shelf. The app labels the source as `starter` and the importer marks them as
estimated, so the UI can say so. A scanned OpenFoodFacts product or a user-created product always wins
over these.

Scope: this is a starting set for everyday cooking, not an exhaustive database. It is deliberately
smaller than the plan's "~150 items" because 60 honest entries beat 150 guessed ones; it grows from user
entries and OpenFoodFacts lookups.
"""

from __future__ import annotations

import json
from pathlib import Path

# key, English name, Russian name, category, kcal, protein, fat, carbs, fiber, sugar, salt, liquid
ITEMS: list[tuple[str, str, str, str, float, float, float, float, float, float, float, bool]] = [
    # Vegetables
    ("potato", "Potato, raw", "Картофель", "vegetable", 77, 2.0, 0.1, 17.0, 2.2, 0.8, 0.01, False),
    ("carrot", "Carrot", "Морковь", "vegetable", 41, 0.9, 0.2, 9.6, 2.8, 4.7, 0.07, False),
    ("onion", "Onion", "Лук репчатый", "vegetable", 40, 1.1, 0.1, 9.3, 1.7, 4.2, 0.01, False),
    ("garlic", "Garlic", "Чеснок", "vegetable", 149, 6.4, 0.5, 33.1, 2.1, 1.0, 0.02, False),
    ("tomato", "Tomato", "Помидор", "vegetable", 18, 0.9, 0.2, 3.9, 1.2, 2.6, 0.01, False),
    ("cucumber", "Cucumber", "Огурец", "vegetable", 15, 0.7, 0.1, 3.6, 0.5, 1.7, 0.01, False),
    ("cabbage", "White cabbage", "Капуста белокочанная", "vegetable", 25, 1.3, 0.1, 5.8, 2.5, 3.2, 0.02, False),
    ("broccoli", "Broccoli", "Брокколи", "vegetable", 34, 2.8, 0.4, 6.6, 2.6, 1.7, 0.03, False),
    ("cauliflower", "Cauliflower", "Цветная капуста", "vegetable", 25, 1.9, 0.3, 5.0, 2.0, 1.9, 0.03, False),
    ("bell_pepper", "Bell pepper", "Перец болгарский", "vegetable", 26, 1.0, 0.3, 6.0, 2.1, 4.2, 0.01, False),
    ("zucchini", "Zucchini", "Кабачок", "vegetable", 17, 1.2, 0.3, 3.1, 1.0, 2.5, 0.01, False),
    ("beetroot", "Beetroot", "Свёкла", "vegetable", 43, 1.6, 0.2, 9.6, 2.8, 6.8, 0.08, False),
    ("pumpkin", "Pumpkin", "Тыква", "vegetable", 26, 1.0, 0.1, 6.5, 0.5, 2.8, 0.01, False),
    ("spinach", "Spinach", "Шпинат", "vegetable", 23, 2.9, 0.4, 3.6, 2.2, 0.4, 0.08, False),
    ("mushrooms", "Mushrooms, champignon", "Шампиньоны", "vegetable", 22, 3.1, 0.3, 3.3, 1.0, 2.0, 0.01, False),
    ("green_peas", "Green peas, frozen", "Зелёный горошек", "vegetable", 81, 5.4, 0.4, 14.5, 5.1, 5.7, 0.01, False),
    ("sweet_corn", "Sweet corn, canned", "Кукуруза сладкая", "vegetable", 86, 3.2, 1.2, 19.0, 2.7, 3.2, 0.24, False),
    # Fruit
    ("apple", "Apple", "Яблоко", "fruit", 52, 0.3, 0.2, 13.8, 2.4, 10.4, 0.0, False),
    ("banana", "Banana", "Банан", "fruit", 89, 1.1, 0.3, 22.8, 2.6, 12.2, 0.0, False),
    ("orange", "Orange", "Апельсин", "fruit", 47, 0.9, 0.1, 11.8, 2.4, 9.4, 0.0, False),
    ("pear", "Pear", "Груша", "fruit", 57, 0.4, 0.1, 15.2, 3.1, 9.8, 0.0, False),
    ("grapes", "Grapes", "Виноград", "fruit", 69, 0.7, 0.2, 18.1, 0.9, 15.5, 0.0, False),
    ("strawberry", "Strawberry", "Клубника", "fruit", 32, 0.7, 0.3, 7.7, 2.0, 4.9, 0.0, False),
    ("blueberry", "Blueberry", "Черника", "fruit", 57, 0.7, 0.3, 14.5, 2.4, 10.0, 0.0, False),
    ("lemon", "Lemon", "Лимон", "fruit", 29, 1.1, 0.3, 9.3, 2.8, 2.5, 0.0, False),
    ("avocado", "Avocado", "Авокадо", "fruit", 160, 2.0, 14.7, 8.5, 6.7, 0.7, 0.01, False),
    # Grains and starches
    ("oats", "Oats, dry", "Овсяные хлопья", "grain", 389, 16.9, 6.9, 66.3, 10.6, 1.0, 0.0, False),
    ("rice_white", "Rice, white, dry", "Рис белый, сухой", "grain", 365, 7.1, 0.7, 80.0, 1.3, 0.1, 0.01, False),
    ("buckwheat", "Buckwheat, dry", "Гречка, сухая", "grain", 343, 13.3, 3.4, 71.5, 10.0, 0.0, 0.01, False),
    ("pasta", "Pasta, dry", "Макароны, сухие", "grain", 371, 13.0, 1.5, 74.7, 3.2, 2.7, 0.01, False),
    ("flour_wheat", "Wheat flour", "Мука пшеничная", "grain", 364, 10.3, 1.0, 76.3, 2.7, 0.3, 0.0, False),
    ("bread_white", "Bread, white", "Хлеб белый", "grain", 265, 9.0, 3.2, 49.0, 2.7, 5.0, 1.20, False),
    ("bread_wholemeal", "Bread, wholemeal", "Хлеб цельнозерновой", "grain", 247, 13.0, 3.4, 41.0, 7.0, 4.0, 1.10, False),
    ("quinoa", "Quinoa, dry", "Киноа, сухая", "grain", 368, 14.1, 6.1, 64.2, 7.0, 0.0, 0.01, False),
    ("couscous", "Couscous, dry", "Кускус, сухой", "grain", 376, 12.8, 0.6, 77.4, 5.0, 0.0, 0.01, False),
    # Dairy and eggs
    ("milk", "Milk, 3.2%", "Молоко 3,2%", "dairy", 60, 2.9, 3.2, 4.7, 0.0, 4.7, 0.10, True),
    ("kefir", "Kefir, 2.5%", "Кефир 2,5%", "dairy", 53, 2.9, 2.5, 4.0, 0.0, 4.0, 0.10, True),
    ("yogurt_plain", "Yogurt, plain, 3.2%", "Йогурт натуральный 3,2%", "dairy", 66, 3.4, 3.2, 4.9, 0.0, 4.9, 0.10, True),
    ("cottage_cheese", "Cottage cheese, 5%", "Творог 5%", "dairy", 121, 17.2, 5.0, 1.8, 0.0, 1.8, 0.10, False),
    ("cheese_hard", "Cheese, hard", "Сыр твёрдый", "dairy", 402, 25.0, 33.0, 0.0, 0.0, 0.0, 1.80, False),
    ("butter", "Butter", "Масло сливочное", "dairy", 717, 0.9, 81.1, 0.1, 0.0, 0.1, 0.02, False),
    ("sour_cream", "Sour cream, 20%", "Сметана 20%", "dairy", 206, 2.8, 20.0, 3.2, 0.0, 3.2, 0.10, False),
    ("egg", "Egg, chicken", "Яйцо куриное", "dairy", 155, 12.6, 10.6, 1.1, 0.0, 1.1, 0.35, False),
    # Meat and fish
    ("chicken_breast", "Chicken breast, raw", "Куриная грудка", "meat", 120, 22.5, 2.6, 0.0, 0.0, 0.0, 0.15, False),
    ("chicken_thigh", "Chicken thigh, raw", "Куриное бедро", "meat", 185, 19.0, 12.0, 0.0, 0.0, 0.0, 0.15, False),
    ("beef", "Beef, lean, raw", "Говядина нежирная", "meat", 187, 20.0, 12.0, 0.0, 0.0, 0.0, 0.15, False),
    ("pork", "Pork, raw", "Свинина", "meat", 263, 18.0, 21.0, 0.0, 0.0, 0.0, 0.15, False),
    ("turkey", "Turkey breast, raw", "Индейка, грудка", "meat", 104, 22.0, 1.5, 0.0, 0.0, 0.0, 0.15, False),
    ("salmon", "Salmon, raw", "Лосось", "fish", 208, 20.0, 13.4, 0.0, 0.0, 0.0, 0.15, False),
    ("cod", "Cod, raw", "Треска", "fish", 82, 17.8, 0.7, 0.0, 0.0, 0.0, 0.20, False),
    ("tuna_canned", "Tuna, canned in water", "Тунец в воде", "fish", 116, 25.5, 0.8, 0.0, 0.0, 0.0, 0.80, False),
    ("shrimp", "Shrimp, raw", "Креветки", "fish", 106, 20.3, 1.7, 0.9, 0.0, 0.0, 0.60, False),
    # Legumes, nuts, seeds
    ("lentils", "Lentils, dry", "Чечевица, сухая", "legume", 352, 24.6, 1.1, 63.4, 10.7, 2.0, 0.01, False),
    ("chickpeas", "Chickpeas, dry", "Нут, сухой", "legume", 364, 19.3, 6.0, 61.0, 17.4, 10.7, 0.02, False),
    ("beans_red", "Kidney beans, dry", "Фасоль красная, сухая", "legume", 333, 23.6, 0.8, 60.0, 24.9, 2.1, 0.01, False),
    ("peanuts", "Peanuts", "Арахис", "nut", 567, 25.8, 49.2, 16.1, 8.5, 4.7, 0.02, False),
    ("almonds", "Almonds", "Миндаль", "nut", 579, 21.2, 49.9, 21.6, 12.5, 4.4, 0.01, False),
    ("walnuts", "Walnuts", "Грецкий орех", "nut", 654, 15.2, 65.2, 13.7, 6.7, 2.6, 0.01, False),
    ("sunflower_seeds", "Sunflower seeds", "Семечки подсолнечника", "nut", 584, 20.8, 51.5, 20.0, 8.6, 2.6, 0.01, False),
    # Fats, sauces, sweeteners
    ("olive_oil", "Olive oil", "Масло оливковое", "fat", 884, 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, True),
    ("sunflower_oil", "Sunflower oil", "Масло подсолнечное", "fat", 884, 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, True),
    ("mayonnaise", "Mayonnaise", "Майонез", "sauce", 680, 1.0, 75.0, 3.0, 0.0, 2.0, 1.00, False),
    ("soy_sauce", "Soy sauce", "Соевый соус", "sauce", 53, 8.1, 0.6, 4.1, 0.8, 0.4, 15.0, True),
    ("ketchup", "Ketchup", "Кетчуп", "sauce", 102, 1.3, 0.1, 23.5, 0.9, 20.0, 2.50, False),
    ("honey", "Honey", "Мёд", "sweet", 304, 0.3, 0.0, 82.4, 0.2, 82.1, 0.01, False),
    ("sugar", "Sugar, white", "Сахар", "sweet", 400, 0.0, 0.0, 100.0, 0.0, 100.0, 0.0, False),
    ("chocolate_dark", "Chocolate, dark 70%", "Шоколад тёмный 70%", "sweet", 598, 7.8, 42.6, 45.9, 10.9, 24.0, 0.02, False),
    # Other
    ("tofu", "Tofu", "Тофу", "other", 76, 8.1, 4.8, 1.9, 0.3, 0.6, 0.01, False),
    ("olives", "Olives, canned", "Оливки", "other", 115, 0.8, 10.7, 6.3, 3.2, 0.0, 3.00, False),
]

OUT_PATH = Path("data/src/main/assets/starter_products.json")


def main() -> None:
    items = [
        {
            "key": key,
            "name": name,
            "name_ru": name_ru,
            "category": category,
            "kcal_100g": kcal,
            "protein_100g": protein,
            "fat_100g": fat,
            "carbs_100g": carbs,
            "fiber_100g": fiber,
            "sugar_100g": sugar,
            "salt_100g": salt,
            "is_liquid": liquid,
        }
        for key, name, name_ru, category, kcal, protein, fat, carbs, fiber, sugar, salt, liquid in ITEMS
    ]

    keys = [item["key"] for item in items]
    duplicates = {key for key in keys if keys.count(key) > 1}
    if duplicates:
        raise SystemExit(f"duplicate keys: {sorted(duplicates)}")

    for item in items:
        if not (0 < item["kcal_100g"] < 950):
            raise SystemExit(f"implausible energy for {item['key']}: {item['kcal_100g']}")
        if not item["name"] or not item["name_ru"]:
            raise SystemExit(f"missing name for {item['key']}")

    payload = {
        "version": 1,
        "note": (
            "Starter ingredient pack. Values are typical composition figures from public food-composition "
            "tables per 100 g (per 100 ml for liquids). They are estimates for generic ingredients, not "
            "measurements of a specific product. Generated by tools/generate_starter_pack.py."
        ),
        "items": items,
    }

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(items)} items to {OUT_PATH}")


if __name__ == "__main__":
    main()
