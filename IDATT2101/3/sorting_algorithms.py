import random, time

# Sorteringsalgoritmer
def partition(arr, left, right):
    p = random.randint(left, right)
    arr[p], arr[right] = arr[right], arr[p]
    pivot = arr[right]
    i = left
    for j in range(left, right):
        if arr[j] < pivot:
            arr[i], arr[j] = arr[j], arr[i]
            i += 1

    arr[i], arr[right] = arr[right], arr[i]
    k = i + 1
    for j in range(i + 1, right + 1):
        if arr[j] == pivot:
            arr[j], arr[k] = arr[k], arr[j]
            k += 1
    return (i + k - 1) // 2

def quicksort(arr, left, right):
    if left < right:
        pivot_index = partition(arr, left, right)
        quicksort(arr, left, pivot_index - 1)
        quicksort(arr, pivot_index + 1, right)

def insertion_sort(arr, left, right):
    for i in range(left + 1, right + 1):
        key = arr[i]
        j = i - 1
        while j >= left and arr[j] > key:
            arr[j + 1] = arr[j]
            j -= 1
        arr[j + 1] = key

def quicksort_hybrid(arr, left, right, threshold):
    if right - left <= threshold:
        insertion_sort(arr, left, right)
    else:
        if left < right:
            pivot_index = partition(arr, left, right)
            quicksort_hybrid(arr, left, pivot_index - 1, threshold)
            quicksort_hybrid(arr, pivot_index + 1, right, threshold)

# Validering av sorteringslogikk
def is_nondecreasing(a):
    return all(a[i] >= a[i-1] for i in range(1, len(a)))

def verify_sorted_and_checksum(sorted_arr, base_checksum):
    assert is_nondecreasing(sorted_arr), "Feil: ikke sortert."
    assert sum(sorted_arr) == base_checksum, "Feil: sjekksum endret."

def verify_resort_time(first_time, resort_time, tolerance=0.5):
    limit = first_time * (1 + tolerance)

    assert resort_time <= limit, (
        f"FEIL: Resortering tok {resort_time:.4f}s vs første sortering: {first_time:.4f}s "
        f"Resortering tok >50% lenger tid enn første sortering."
    )

# Måling av ulike grenser for quicksort + innsettingssortering
def benchmark_thresholds(thresholds, arr):
    results = []
    for t in thresholds:
        arr_copy = arr.copy()
        start = time.perf_counter()
        quicksort_hybrid(arr_copy, 0, len(arr_copy)-1, t)
        end = time.perf_counter()
        results.append(end - start)
    return thresholds, results



