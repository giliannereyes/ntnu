import numpy as np
from algorithms import quicksort, quicksort_hybrid, verify_sorted_and_checksum, benchmark_thresholds, verify_resort_time
import random, time, matplotlib.pyplot as plt

def make_dataset(n):
    return [random.randint(0, 1_000_000) if i % 2 == 0 else 42 for i in range(n)]

def measure_time_qs(arr):
    arr_copy = arr.copy()
    start = time.perf_counter()
    quicksort(arr_copy, 0, len(arr_copy) - 1)
    end = time.perf_counter()
    return arr_copy, end - start

def measure_time_hs(arr, thr):
    arr_copy = arr.copy()
    start = time.perf_counter()
    quicksort_hybrid(arr_copy, 0, len(arr_copy) - 1, thr)
    end = time.perf_counter()
    return arr_copy, end - start

# Sammenligning av quicksort og quicksort + innsettingssortering
threshold = 25
for i in range(5):
    data = make_dataset(100_000)
    base_checksum = sum(data)
    arr_qs, time_qs = measure_time_qs(data)
    arr_hs, time_hs = measure_time_hs(data, threshold)

    # Validere at begge tabellene er sortert og har samme sjekksum
    verify_sorted_and_checksum(arr_qs, base_checksum)
    verify_sorted_and_checksum(arr_qs, base_checksum)

    print(f"\nQuicksort: {time_qs:.4f}s")
    print(f"Quicksort + innsettingssortering: {time_hs:.4f}s")
    if time_qs > time_hs:
        print(f"Test OK. Forskjell: {time_qs - time_hs:.4f}")

    # Sortere tabellene som allerede er sortert
    arr_qs_copy, time_qs_sorted = measure_time_qs(arr_qs)
    arr_hs_copy, time_hs_sorted = measure_time_hs(arr_hs, threshold)
    verify_resort_time(time_qs, time_qs_sorted)
    verify_resort_time(time_hs, time_hs_sorted)


# Finne beste størrelse for innsettingssortering
thresholds = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50]
all_times = []
datasets = [make_dataset(100_000) for _ in range(10)]
for data in datasets:
    ths, times = benchmark_thresholds(thresholds, data.copy())
    all_times.append(times)
avg_times = np.mean(all_times, axis=0)

plt.figure(figsize=(8, 5))
plt.plot(thresholds, avg_times, marker="o", linestyle="-", color="b", label="Kjøretid for quicksort og innsettingssortering")
plt.xlabel("Grense for innsettingssortering")
plt.ylabel("Tid for sortering (sekund)")
plt.title("Måleserie: Grense vs. Kjøretid for quicksort og innsettingssortering")
plt.grid(True)
plt.legend()
plt.show()

# 25 ser ut til å være størrelsen for å bytte til innsettingssortering som gir raskest sortering.
