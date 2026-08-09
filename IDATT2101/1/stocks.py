import time, random

def max_profit(changes):
    n = len(changes)

    # Find prices
    prices = [0] * (n + 1)
    for i in range(1, n + 1):
        prices[i] = prices[i - 1] + changes[i - 1]

    min_price = prices[0]
    min_day = 0         
    max_profit_value = 0
    buy_day = 0
    sell_day = 0

    for i in range(1, n + 1):
        profit = prices[i] - min_price
        if profit > max_profit_value:
            max_profit_value = profit
            buy_day = min_day  
            sell_day = i

        if prices[i] < min_price:
            min_price = prices[i]
            min_day = i

    return buy_day, sell_day, max_profit_value

# Sjekk for oppgave 1-1 
changes = [-1, 3, -9, 2, 2, -1, 2, -1, -5]
buy_day, sell_day, profit = max_profit(changes)
print(f"Det lønner seg best å kjøpe på dag {buy_day} og selge på dag {sell_day}.")

# Tidsmålinger (oppgave 1-2)
ns = [100_000, 200_000, 400_000, 800_000, 1_600_000, 3_200_000]
times = []
for n in ns:
    changes = [random.randint(-9, 9) for _ in range(n)]
    t0 = time.perf_counter()
    res = max_profit(changes)
    t1 = time.perf_counter()
    times.append((n, t1 - t0))

for n, t in times:
    print(f"n = {n}: {t:.3f} s")
