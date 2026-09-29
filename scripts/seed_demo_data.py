#!/usr/bin/env python3
"""向本地 SQLite 库写入一套完整、自洽的演示数据，用来截图展示总览、快照、大事记、礼金、借款等页面。

脚本写入的每一行 id 都以 ``demo-`` 开头（汇率行用固定的 fetched_at 标记），可以精确删除。

用法：
    python3 scripts/seed_demo_data.py --user <用户名>                      # 追加：先删该用户旧演示数据，再写入
    python3 scripts/seed_demo_data.py --user <用户名> --reset              # 清空该用户全部账户/快照/大事记/礼金/借款后写入
    python3 scripts/seed_demo_data.py --user <用户名> --reset --locale en  # 英文演示数据（账户名、描述、成员名、选项标签）
    python3 scripts/seed_demo_data.py --user <用户名> --clean              # 只删除该用户的演示数据（不还原选项标签）
    python3 scripts/seed_demo_data.py --user <用户名> --db path.db         # 指定数据库文件

任何写操作之前都会用 sqlite3 备份 API 在库文件旁生成 ``<db>.bak-<时间戳>``。
--reset 默认只允许作用于仓库 backend/data/ 下的库文件，其他路径需加 --force。

数据为固定随机种子生成，重复执行结果一致（id 与备份文件名除外）。
"""
from __future__ import annotations

import argparse
import calendar
import math
import random
import sqlite3
import uuid
from datetime import date, datetime
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATA_DIR = REPO / "backend" / "data"
DEFAULT_DB = DATA_DIR / "zero.db"
PREFIX = "demo-"
SEED = 20260927
# 演示汇率行的 fetched_at 标记：合法的 ISO 时间，便于识别和精确删除
FX_MARKER = "1970-01-01T00:00:00Z"

CNY, USD, HKD = 156, 840, 344

# 起止月份：2024-10 ~ 2026-08 每月月末一次，外加 2026-09-26 作为最新快照
START = (2024, 10)
END = (2026, 8)
LATEST = date(2026, 9, 26)


def demo_id() -> str:
    return PREFIX + uuid.uuid4().hex[:24]


def month_ends() -> list[date]:
    out = []
    y, m = START
    while (y, m) <= END:
        out.append(date(y, m, calendar.monthrange(y, m)[1]))
        m += 1
        if m > 12:
            y, m = y + 1, 1
    out.append(LATEST)
    return out


# ---------------------------------------------------------------------------
# 选项标签：成员名称，以及英文界面下的账户类型、大事记分类
# ---------------------------------------------------------------------------

OPTION_LABELS = {
    "zh": {
        "account_type": [("cash", "现金"), ("deposit", "固收"), ("fund", "基金"), ("pension", "养老"),
                         ("housing_fund", "公积金"), ("credit", "负债")],
        "account_owner": [("A", "阿哲"), ("B", "小雨"), ("shared", "共同")],
        "event_category": [("rent", "房租"), ("travel", "旅行"), ("medical", "医疗"), ("appliance", "家电装修"),
                           ("social", "人情往来"), ("other", "其他")],
    },
    "en": {
        "account_type": [("cash", "Cash"), ("deposit", "Fixed income"), ("fund", "Funds"), ("pension", "Pension"),
                         ("housing_fund", "Housing fund"), ("credit", "Liabilities")],
        "account_owner": [("A", "Alex"), ("B", "Jamie"), ("shared", "Joint")],
        "event_category": [("rent", "Rent"), ("travel", "Travel"), ("medical", "Medical"), ("appliance", "Home & appliances"),
                           ("social", "Social"), ("other", "Other")],
    },
}


# ---------------------------------------------------------------------------
# 账户与余额模型
# ---------------------------------------------------------------------------

def balance_models(rng: random.Random):
    """返回各类余额模型的构造函数：模型输入第 i 个快照（0 起），返回原币余额。负债返回负数。"""

    # 大额支出月份（端午出游、日本旅行）现金明显减少
    cash_dips = {8: 0.35, 18: 0.45}

    def salary(base, spread):
        # 春节前后（第 4、16 个快照，即 2 月）年终奖到账，工资卡余额偏高
        return lambda i: round(
            (base + rng.uniform(-spread, spread) + (base * 0.8 if i % 12 == 4 else 0)) * (1 - cash_dips.get(i, 0)),
            2,
        )

    def stepped(start, step, every):
        return lambda i: float(start + step * (i // every))

    def growing(start, monthly, noise=0.0):
        return lambda i: round(start + monthly * i + (rng.uniform(-noise, noise) if noise else 0), 2)

    # 市场回撤月份（第 i 个快照）→ 额外跌幅，让曲线有起伏
    shocks = {5: -0.11, 6: -0.04, 13: -0.14, 19: -0.08}

    def market(start, contribution, drift, vol):
        state = {"v": start}

        def f(i):
            if i:
                state["v"] = state["v"] * (1 + rng.gauss(drift, vol) + shocks.get(i, 0)) + contribution
            return round(state["v"], 2)

        return f

    def pension(start, yearly, month):
        # 每年指定月份一次性缴存
        return lambda i: float(start + yearly * sum(1 for k in range(i + 1) if (START[1] - 1 + k) % 12 == month - 1))

    def card(low, high):
        return lambda i: -round(rng.uniform(low, high), 2)

    def amortize(start, monthly):
        return lambda i: -max(0.0, round(start - monthly * i, 2))

    return salary, stepped, growing, market, pension, card, amortize


def build_accounts(rng: random.Random):
    """账户清单：(key, type, owner, currency, 中文名, 英文名, 余额模型)。"""
    salary, stepped, growing, market, pension, card, amortize = balance_models(rng)
    return [
        # 现金
        ("cmb_payroll", "cash", "A", CNY, "招商银行工资卡", "CMB Payroll Card", salary(26000, 6000)),
        ("ccb_payroll", "cash", "B", CNY, "建设银行工资卡", "CCB Payroll Card", salary(15500, 3500)),
        ("wechat", "cash", "shared", CNY, "微信零钱", "WeChat Pay Balance", salary(2400, 1100)),
        ("yuebao", "cash", "shared", CNY, "支付宝余额宝", "Alipay Yu'e Bao", growing(36000, 900, 2500)),
        # 固收
        ("cmb_cd", "deposit", "shared", CNY, "招行大额存单", "CMB Large-Denomination CD", stepped(150000, 50000, 8)),
        ("ncd_index", "deposit", "shared", CNY, "同业存单指数", "Interbank CD Index Fund", growing(60000, 1600, 400)),
        ("hk_savings", "deposit", "B", HKD, "香港银行储蓄", "HSBC HK Savings (HKD)", stepped(80000, 10000, 3)),
        # 基金
        ("csi300", "fund", "shared", CNY, "沪深300指数定投", "CSI 300 Index DCA", market(68000, 3000, 0.005, 0.045)),
        ("sp500_qdii", "fund", "A", CNY, "标普500 QDII", "S&P 500 QDII Fund", market(52000, 2000, 0.009, 0.035)),
        ("us_broker", "fund", "A", USD, "美股券商账户", "IBKR Brokerage (USD)", market(16500, 400, 0.016, 0.045)),
        # 养老
        ("private_pension", "pension", "A", CNY, "个人养老金", "Private Pension", pension(24000, 12000, 12)),
        ("annuity", "pension", "B", CNY, "企业年金", "Enterprise Annuity", growing(38000, 1100)),
        # 公积金
        ("hf_a", "housing_fund", "A", CNY, "住房公积金（阿哲）", "Housing Fund – Alex", growing(96000, 3600)),
        ("hf_b", "housing_fund", "B", CNY, "住房公积金（小雨）", "Housing Fund – Jamie", growing(64000, 2400)),
        # 负债
        ("credit_card", "credit", "A", CNY, "招行信用卡", "CMB Credit Card", card(3200, 11800)),
        ("car_loan", "credit", "shared", CNY, "车贷", "Car Loan", amortize(128000, 3500)),
    ]


def fx_rate(currency: int, i: int, n: int) -> float:
    """演示汇率：1 单位外币折合人民币，从起点平滑走向终点并带轻微波动（非真实行情）。"""
    t = i / max(1, n - 1)
    if currency == USD:
        start, end, wiggle, digits = 7.25, 6.71, 0.035, 4
    else:
        start, end, wiggle, digits = 0.93, 0.855, 0.0045, 5
    return round(start + (end - start) * t + wiggle * math.sin(i * 0.9) * (1 - t * 0.5), digits)


# ---------------------------------------------------------------------------
# 大事记、礼金、借款（中英文）
# ---------------------------------------------------------------------------

# 大事记：金额为负表示支出
EVENT_POOL = {
    "rent": ("rent", "房租", "Monthly rent", (-5800, -5800)),
    "golden_week": ("travel", "国庆自驾游", "National Day road trip", (-9800, -6200)),
    "cny_flight": ("travel", "春节回家机票", "Flights home for Spring Festival", (-7600, -4800)),
    "bonus": ("other", "年终奖到账", "Year-end bonus", (38000, 52000)),
    "summer": ("travel", "端午云南旅行", "Dragon Boat trip to Yunnan", (-12800, -8600)),
    "japan": ("travel", "日本关西旅行", "Trip to Kansai, Japan", (-19800, -13600)),
    "checkup": ("medical", "年度体检", "Annual health check", (-1800, -900)),
    "dentist": ("medical", "看牙补牙", "Dental treatment", (-3800, -1200)),
    "washer": ("appliance", "换洗烘套装", "New washer & dryer", (-7600, -4800)),
    "decor": ("appliance", "客厅软装", "Living room refresh", (-12800, -6800)),
    "dinner": ("social", "朋友聚餐", "Dinner with friends", (-1200, -600)),
    "parents": ("social", "给父母的生活费", "Allowance for parents", (-3000, -2000)),
    "redeem": ("other", "理财到期赎回", "Wealth product matured", (3000, 9000)),
}
RANDOM_EVENTS = ["checkup", "dentist", "washer", "decor", "dinner", "parents", "redeem"]

SNAPSHOT_NOTES = {
    "bonus": ("年终奖到账，补仓指数基金", "Year-end bonus in; topped up index funds"),
    "rebalance": ("季度再平衡", "Quarterly rebalance"),
    "latest": ("月底前临时盘点", "Check-in before month end"),
}

GIFT_RECIPIENTS = [
    # (中文名, 中文关系, 中文备注), (英文名, 英文关系, 英文备注)
    (("张伟 & 林晓", "大学同学", "每年春节会互相走动"), ("Wei & Lin Zhang", "College classmates", "We visit each other every Spring Festival")),
    (("王阿姨一家", "邻居", None), ("The Wangs", "Neighbours", None)),
    (("李娜", "同事", "隔壁组的产品经理"), ("Nina Li", "Colleague", "Product manager on the next team")),
    (("陈浩", "发小", None), ("Hao Chen", "Childhood friend", None)),
    (("表姐一家", "亲戚", "住在杭州"), ("Cousin Mei's family", "Family", "Lives in Hangzhou")),
    (("赵磊", "前同事", None), ("Leo Zhao", "Former colleague", None)),
    (("刘老师", "导师", "研究生导师"), ("Prof. Liu", "Mentor", "Graduate school advisor")),
    (("周婷", "大学室友", None), ("Tina Zhou", "College roommate", None)),
]

OCCASIONS = {
    "wedding": ("结婚", "Wedding"),
    "baby": ("满月", "Baby's first month"),
    "house": ("乔迁", "Housewarming"),
    "birthday": ("生日", "Birthday"),
    "visit": ("探望", "Hospital visit"),
    "red_packet": ("春节红包", "Lunar New Year red envelope"),
    "thanks": ("谢师宴", "Thank-you dinner"),
    "school": ("升学", "Starting school"),
}
METHODS = {
    "wechat": ("微信", "WeChat Pay"),
    "cash": ("现金", "Cash"),
    "alipay": ("支付宝", "Alipay"),
    "bank": ("银行转账", "Bank transfer"),
}

GIFT_RECORDS = [
    # recipient_index, occasion, date, amount, method, (中文备注, 英文备注)
    (0, "wedding", "2025-05-18", 2000, "wechat", ("婚礼在苏州", "Wedding in Suzhou")),
    (3, "wedding", "2025-10-03", 2600, "cash", None),
    (7, "wedding", "2026-05-01", 1600, "wechat", None),
    (4, "baby", "2025-01-12", 1000, "cash", ("表姐家二宝", "Cousin's second baby")),
    (0, "baby", "2026-03-22", 1200, "wechat", None),
    (1, "house", "2025-08-09", 800, "wechat", None),
    (5, "house", "2026-06-14", 600, "alipay", None),
    (2, "birthday", "2025-11-20", 300, "wechat", None),
    (2, "birthday", "2026-07-18", 300, "wechat", None),
    (6, "visit", "2025-12-06", 500, "cash", ("老师住院", "Prof. Liu was in hospital")),
    (4, "red_packet", "2025-01-28", 2000, "cash", ("给两个小朋友", "For the two kids")),
    (4, "red_packet", "2026-02-16", 2400, "cash", None),
    (1, "red_packet", "2026-02-17", 400, "cash", None),
    (6, "thanks", "2026-06-28", 800, "bank", None),
    (3, "school", "2026-08-30", 1000, "wechat", ("孩子上小学", "Kid starts primary school")),
]

LOANS = [
    # (中文借款人, 中文关系, 中文备注), (英文...), amount, loan_date, due_date, repayments[(amount, date, (中文备注, 英文备注))]
    (("陈浩", "发小", "装修周转"), ("Hao Chen", "Childhood friend", "Short-term help with renovation"),
     20000, "2025-03-10", "2025-12-31", [
         (5000, "2025-06-10", ("第一笔", "First instalment")),
         (5000, "2025-09-10", None),
         (10000, "2025-12-20", ("还清", "Paid off")),
     ]),
    (("表弟小宇", "亲戚", "考研报班"), ("Cousin Yu", "Family", "Grad-school exam prep course"),
     8000, "2025-09-01", "2026-03-01", [
         (3000, "2026-01-15", None),
     ]),
    (("李娜", "同事", "临时周转"), ("Nina Li", "Colleague", "Short-term cash flow"),
     3000, "2026-04-12", "2026-06-30", []),
    (("赵磊", "前同事", "创业启动资金"), ("Leo Zhao", "Former colleague", "Seed money for his startup"),
     15000, "2026-06-05", "2026-12-31", [
         (2000, "2026-08-05", ("微信转账", "Via WeChat Pay")),
         (2000, "2026-09-05", None),
     ]),
    (("周婷", "大学室友", "代付演唱会门票"), ("Tina Zhou", "College roommate", "Concert tickets I paid for"),
     1200, "2026-08-18", None, [
         (1200, "2026-09-02", None),
     ]),
]


def pick(pair, locale: str):
    """从 (中文, 英文) 中取对应语言；None 原样返回。"""
    if pair is None:
        return None
    return pair[0] if locale == "zh" else pair[1]


# ---------------------------------------------------------------------------
# 备份、清理
# ---------------------------------------------------------------------------

def backup(db: Path) -> Path:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    dst_path = db.with_name(f"{db.name}.bak-{stamp}")
    n = 1
    while dst_path.exists():  # 同一秒内多次运行时不覆盖已有备份
        dst_path = db.with_name(f"{db.name}.bak-{stamp}-{n}")
        n += 1
    src = sqlite3.connect(db)
    dst = sqlite3.connect(dst_path)
    try:
        src.backup(dst)
    finally:
        dst.close()
        src.close()
    return dst_path


def user_id_of(conn: sqlite3.Connection, username: str) -> str:
    row = conn.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()
    if not row:
        raise SystemExit(f"找不到用户 {username}")
    return row[0]


def clean(conn: sqlite3.Connection, user_id: str) -> None:
    """删除该用户的演示数据（id 以 demo- 开头的行）；其他用户的数据不受影响。

    演示汇率按日期共用，只有库里已经没有任何演示快照时才一并删除。
    """
    like = PREFIX + "%"
    cur = conn.cursor()
    snaps = "SELECT id FROM snapshots WHERE user_id = ? AND id LIKE ?"
    accs = "SELECT id FROM accounts WHERE user_id = ? AND id LIKE ?"
    loans = "SELECT id FROM loans WHERE user_id = ? AND id LIKE ?"
    recipients = "SELECT id FROM gift_recipients WHERE user_id = ? AND id LIKE ?"
    cur.execute(f"DELETE FROM loan_repayments WHERE loan_id IN ({loans})", (user_id, like))
    cur.execute("DELETE FROM loans WHERE user_id = ? AND id LIKE ?", (user_id, like))
    cur.execute(f"DELETE FROM gift_records WHERE gift_recipient_id IN ({recipients})", (user_id, like))
    cur.execute("DELETE FROM gift_records WHERE user_id = ? AND id LIKE ?", (user_id, like))
    cur.execute("DELETE FROM gift_recipients WHERE user_id = ? AND id LIKE ?", (user_id, like))
    cur.execute(f"DELETE FROM events WHERE snapshot_id IN ({snaps})", (user_id, like))
    # 演示快照下的余额行，以及该用户真实快照里指向演示账户的余额行
    cur.execute(
        f"DELETE FROM snapshot_items WHERE snapshot_id IN ({snaps}) OR account_id IN ({accs})",
        (user_id, like, user_id, like),
    )
    cur.execute("DELETE FROM snapshots WHERE user_id = ? AND id LIKE ?", (user_id, like))
    cur.execute("DELETE FROM accounts WHERE user_id = ? AND id LIKE ?", (user_id, like))
    if not cur.execute("SELECT 1 FROM snapshots WHERE id LIKE ? LIMIT 1", (like,)).fetchone():
        cur.execute("DELETE FROM fx_rates WHERE fetched_at = ?", (FX_MARKER,))


def wipe_user(conn: sqlite3.Connection, user_id: str) -> None:
    """删除该用户的全部账户、快照、大事记、礼金、借款（按外键依赖顺序）。"""
    cur = conn.cursor()
    snaps = "SELECT id FROM snapshots WHERE user_id = ?"
    accs = "SELECT id FROM accounts WHERE user_id = ?"
    cur.execute("DELETE FROM loan_repayments WHERE user_id = ? OR loan_id IN (SELECT id FROM loans WHERE user_id = ?)", (user_id, user_id))
    cur.execute("DELETE FROM loans WHERE user_id = ?", (user_id,))
    cur.execute(
        "DELETE FROM gift_records WHERE user_id = ? OR gift_recipient_id IN (SELECT id FROM gift_recipients WHERE user_id = ?)",
        (user_id, user_id),
    )
    cur.execute("DELETE FROM gift_recipients WHERE user_id = ?", (user_id,))
    cur.execute(f"DELETE FROM events WHERE snapshot_id IN ({snaps})", (user_id,))
    cur.execute(f"DELETE FROM snapshot_items WHERE snapshot_id IN ({snaps}) OR account_id IN ({accs})", (user_id, user_id))
    cur.execute("DELETE FROM snapshots WHERE user_id = ?", (user_id,))
    cur.execute("DELETE FROM accounts WHERE user_id = ?", (user_id,))


# ---------------------------------------------------------------------------
# 写入
# ---------------------------------------------------------------------------

def set_option_labels(conn: sqlite3.Connection, user_id: str, locale: str) -> int:
    cur = conn.cursor()
    changed = 0
    for dimension, rows in OPTION_LABELS[locale].items():
        for order, (key, label) in enumerate(rows):
            hit = cur.execute(
                "UPDATE user_option_items SET label = ? WHERE user_id = ? AND dimension = ? AND opt_key = ?",
                (label, user_id, dimension, key),
            ).rowcount
            if not hit:
                cur.execute(
                    "INSERT INTO user_option_items (id, user_id, dimension, opt_key, label, sort_order, enabled) VALUES (?, ?, ?, ?, ?, ?, 1)",
                    (uuid.uuid4().hex, user_id, dimension, key, label, order),
                )
            changed += 1
    return changed


def has_real_foreign_items(conn: sqlite3.Connection) -> bool:
    """库里是否已有任何用户的真实（非演示）外币余额。

    fx_rates 按日期全局共用、演示汇率又记为「手动填写」，写入后会改变真实快照的折算结果，
    因此只要存在真实外币数据就不写演示汇率。
    """
    row = conn.execute(
        "SELECT 1 FROM snapshot_items si JOIN snapshots s ON s.id = si.snapshot_id "
        "WHERE s.id NOT LIKE ? AND si.currency <> 156 LIMIT 1",
        (PREFIX + "%",),
    ).fetchone()
    return row is not None


def seed(conn: sqlite3.Connection, user_id: str, locale: str) -> dict[str, int]:
    rng = random.Random(SEED)
    cur = conn.cursor()
    write_fx = not has_real_foreign_items(conn)
    if not write_fx:
        print("提示：库中已有真实外币余额，为避免改变其折算结果，不写入演示汇率；演示外币账户将按库中已有汇率折算")
    zh = locale == "zh"

    # --- 账户：排在用户已有账户之后
    next_order = cur.execute("SELECT COALESCE(MAX(sort_order), 0) FROM accounts WHERE user_id = ?", (user_id,)).fetchone()[0] + 1
    accounts = []
    for key, acc_type, owner, currency, name_zh, name_en, model in build_accounts(rng):
        acc_id = demo_id()
        cur.execute(
            "INSERT INTO accounts (id, user_id, name, type, owner, sort_order, is_active, created_at) VALUES (?, ?, ?, ?, ?, ?, 1, ?)",
            (acc_id, user_id, name_zh if zh else name_en, acc_type, owner, next_order, "2024-10-01 09:00:00"),
        )
        accounts.append((acc_id, acc_type, currency, model))
        next_order += 1

    existing = {d: sid for sid, d in cur.execute("SELECT id, date FROM snapshots WHERE user_id = ?", (user_id,))}
    dates = month_ends()

    # --- 快照 + 余额 + 大事记 + 汇率
    snapshots = items = events = fx = 0
    for i, d in enumerate(dates):
        ds = d.isoformat()
        created_at = f"{ds} 21:{rng.randint(10, 59)}:00"
        if d == LATEST:
            note_key = "latest"
        elif d.month == 2:
            note_key = "bonus"
        elif d.month in (3, 6, 9, 12):
            note_key = "rebalance"
        else:
            note_key = None
        if ds in existing:
            # 追加模式下与真实快照同日：余额行挂到已有快照上，不写大事记
            snap_id, is_new = existing[ds], False
        else:
            snap_id, is_new = demo_id(), True
            cur.execute(
                "INSERT INTO snapshots (id, user_id, date, note, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                (snap_id, user_id, ds, pick(SNAPSHOT_NOTES.get(note_key), locale), created_at, user_id),
            )
            snapshots += 1

        for acc_id, acc_type, currency, model in accounts:
            balance = model(i)
            if acc_type == "credit":
                balance = -abs(balance)
            cur.execute(
                "INSERT INTO snapshot_items (id, snapshot_id, account_id, balance, currency) VALUES (?, ?, ?, ?, ?)",
                (demo_id(), snap_id, acc_id, balance, currency),
            )
            items += 1

        for currency in (USD, HKD) if write_fx else ():
            # 只覆盖不存在的或演示写入的汇率，真实汇率保持不变
            fx += cur.execute(
                "INSERT INTO fx_rates (currency, rate_date, rate, source, provider, fetched_at) VALUES (?, ?, ?, 2, NULL, ?) "
                "ON CONFLICT(currency, rate_date) DO UPDATE SET rate = excluded.rate, source = excluded.source, "
                "provider = excluded.provider WHERE fx_rates.fetched_at = excluded.fetched_at",
                (currency, ds, fx_rate(currency, i, len(dates)), FX_MARKER),
            ).rowcount

        picks = ["rent"]  # 每月房租
        if d.month == 10:
            picks.append("golden_week")
        if d.month == 1:
            picks.append("cny_flight")
        if d.month == 2:
            picks.append("bonus")
        if i == 8:
            picks.append("summer")
        if i == 18:
            picks.append("japan")
        if len(picks) < 3:
            picks += rng.sample(RANDOM_EVENTS, k=rng.randint(0, 3 - len(picks)))
        for key in picks:
            category, desc_zh, desc_en, (lo, hi) = EVENT_POOL[key]
            amount = float(lo if lo == hi else round(rng.uniform(lo, hi), -1))
            if not is_new:
                continue
            cur.execute(
                "INSERT INTO events (id, snapshot_id, category, description, amount, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                (demo_id(), snap_id, category, desc_zh if zh else desc_en, amount, created_at),
            )
            events += 1

    # --- 礼金
    existing_names = {n for (n,) in cur.execute("SELECT name FROM gift_recipients WHERE user_id = ?", (user_id,))}
    recipient_ids = []
    for pair in GIFT_RECIPIENTS:
        name, rel, note = pick(pair, locale)
        if name in existing_names:
            # 追加模式下与真实礼金对象重名：沿用已有对象
            recipient_ids.append(
                cur.execute("SELECT id FROM gift_recipients WHERE user_id = ? AND name = ?", (user_id, name)).fetchone()[0]
            )
            continue
        rid = demo_id()
        cur.execute(
            "INSERT INTO gift_recipients (id, user_id, name, relationship, note, is_active, created_at, updated_at) "
            "VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
            (rid, user_id, name, rel, note, "2024-12-01 10:00:00", "2024-12-01 10:00:00"),
        )
        recipient_ids.append(rid)
    for idx, occasion, gift_date, amount, method, note in GIFT_RECORDS:
        stamp = f"{gift_date} 20:00:00"
        cur.execute(
            "INSERT INTO gift_records (id, user_id, gift_recipient_id, occasion, gift_date, amount, payment_method, note, created_at, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (demo_id(), user_id, recipient_ids[idx], pick(OCCASIONS[occasion], locale), gift_date, amount,
             pick(METHODS[method], locale), pick(note, locale), stamp, stamp),
        )

    # --- 借款
    repayments = 0
    for pair_zh, pair_en, amount, loan_date, due_date, reps in LOANS:
        borrower, rel, note = pair_zh if zh else pair_en
        loan_id = demo_id()
        stamp = f"{loan_date} 20:00:00"
        cur.execute(
            "INSERT INTO loans (id, user_id, borrower_name, relationship, amount, loan_date, due_date, note, created_at, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (loan_id, user_id, borrower, rel, amount, loan_date, due_date, note, stamp, stamp),
        )
        for rep_amount, rep_date, rep_note in reps:
            cur.execute(
                "INSERT INTO loan_repayments (id, user_id, loan_id, amount, repay_date, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (demo_id(), user_id, loan_id, rep_amount, rep_date, pick(rep_note, locale), f"{rep_date} 20:00:00"),
            )
            repayments += 1

    return {
        "账户": len(accounts),
        "快照": snapshots,
        "余额行": items,
        "大事记": events,
        "汇率": fx,
        "礼金对象": len(GIFT_RECIPIENTS),
        "礼金记录": len(GIFT_RECORDS),
        "借款": len(LOANS),
        "还款": repayments,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    parser.add_argument("--user", required=True, help="写入到哪个用户名下")
    parser.add_argument("--locale", choices=("zh", "en"), default="zh", help="演示数据语言")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--clean", action="store_true", help="只删除演示数据")
    mode.add_argument("--reset", action="store_true", help="清空该用户全部账户/快照/大事记/礼金/借款后写入演示数据")
    parser.add_argument("--force", action="store_true", help="允许对 backend/data/ 以外的库执行 --reset")
    args = parser.parse_args()

    db = args.db.resolve()
    if not db.is_file():
        raise SystemExit(f"找不到数据库文件 {db}")
    if args.reset and not args.force and not db.is_relative_to(DATA_DIR.resolve()):
        raise SystemExit(f"--reset 只允许作用于 {DATA_DIR} 下的库；确认无误请加 --force")

    conn = sqlite3.connect(db)
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        user_id = user_id_of(conn, args.user)
        conn.close()
        print(f"已备份数据库：{backup(db)}")
        conn = sqlite3.connect(db)
        conn.execute("PRAGMA foreign_keys = ON")
        with conn:
            clean(conn, user_id)
            if args.clean:
                print(f"已删除用户 {args.user} 的演示数据（id 以 demo- 开头的行）")
                return
            if args.reset:
                wipe_user(conn, user_id)
            # 选项标签是用户自定义数据，只在 --reset 时改成演示名称；--clean 不会还原
            labels = set_option_labels(conn, user_id, args.locale) if args.reset else 0
            stats = seed(conn, user_id, args.locale)
        mode_name = "重置" if args.reset else "追加"
        print(f"演示数据已写入（{mode_name}，{args.locale}，选项标签 {labels} 项）：" + "，".join(f"{k} {v}" for k, v in stats.items()))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
