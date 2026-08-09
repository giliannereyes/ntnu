import time

# Metode 1 
def multiply1(n, x): 
    if (n == 1):
        return x
    else: 
        return x + multiply1(n-1, x)

# Metode 2 
def multiply2(n, x):
    if n == 1:
        return x
    if n & 1:                  
        return x + multiply2(n // 2, x + x)
    else:                  
        return multiply2(n // 2, x + x)
    
# Testkode 
tests = [
    (13, 2.5),
    (14, 10.1),
    (7, 3.3),
    (25, 1.2),
    (50, 0.5),
]
for n, x in tests:
    expected = n * x  
    print(f"Metode 1 | n={n}, x={x} | Forventet: {expected:.2f} | Resultat: {multiply1(n, x):.2f}")
    print(f"Metode 2 | n={n}, x={x} | Forventet: {expected:.2f} | Resultat: {multiply2(n, x):.2f}")

# Tidsmålinger
ns = [10, 20, 40, 80, 160, 320, 640]

def avg_time(func, n, x, reps=10000):
    t0 = time.perf_counter()
    for _ in range(reps):
        func(n, x)
    t1 = time.perf_counter()
    return (t1 - t0) / reps

def format_us(seconds):     
    return f"{seconds * 1_000_000:.2f} µs"

print("\nMetode 1:")
times = []
for n in ns:
    t = avg_time(multiply1, n, 2.5, reps=1000) 
    times.append(t)

for t in times:
    print(f"n={n}: {format_us(t)}")

print("\nMetode 2:")
for n in ns:
    t = avg_time(multiply2, n, 2.5, reps=100000) 
    print(f"n={n}: {format_us(t)}")
