import random as rd 
import time
from sympy import nextprime

class Node:
    def __init__(self, key):
        self.key = key
        self.next = None

class HashTable:
    def __init__(self, table_size):
        self.table_size = table_size
        self.table = [None] * table_size
        self.count = 0
        self.collisions = 0

    def hash1(self, key) -> int:
        if isinstance(key, str):
            key = sum((i + 1) * ord(c) for i, c in enumerate(key)) # Tegn vektes ulikt etter posisjon
        return key % self.table_size

    def insert(self, key):
        pos = self.hash1(key)
        if self.table[pos] is None:
            self.table[pos] = Node(key)
        else:
            current = self.table[pos]
            while current:
                if current.key == key:
                    return  # Ingen duplikater
                if current.next is None:
                    break
                current = current.next # Neste node i lenket liste
            current.next = Node(key) # Ny node i lenket liste
            self.collisions += 1
            print(f"Kollisjon: {current.key} <-> {key}")
        self.count += 1

    def search(self, key) -> bool:
        pos = self.hash1(key)
        current = self.table[pos]
        while current:
            if current.key == key:
                return True
            current = current.next
        return False

    def load_factor(self):
        return self.count / self.table_size

    def stats(self):
        print(f"Antall elementer: {self.count}")
        print(f"Lastfaktor: {self.load_factor():.3f}")
        print(f"Totalt antall kollisjoner: {self.collisions}")
        print(f"Kollisjoner per nøkkel: {self.collisions / self.count:.3f}")

class HashTableLinearProbing(HashTable):
    def __init__(self, table_size):
        super().__init__(table_size)
    
    def insert(self, key):
        if self.count >= self.table_size:
            raise Exception("Hash table is full. Cannot insert new key.")
        
        pos = self.hash1(key)
        start_pos = pos
        
        while self.table[pos] is not None:
            if self.table[pos] == key:
                return  # Ingen duplikater
            
            self.collisions += 1 
            pos = (pos + 1) % self.table_size  # Neste posisjon
            
            if pos == start_pos:
                raise Exception("Hash table is full after probing.")

        self.table[pos] = key
        self.count += 1
    
    def search(self, key: str) -> bool:
        pos = self.hash1(key)
        start_pos = pos
        
        while self.table[pos] is not None:
            if self.table[pos] == key:
                return True
            
            pos = (pos + 1) % self.table_size  # Neste posisjon
            
            if pos == start_pos:
                break
        
        return False

class HashTableDoubleHashing(HashTable):
    def __init__(self, table_size):
        super().__init__(table_size)

    def hash2(self, key) -> int:
        if isinstance(key, str):
            val = sum(ord(c) for c in key)
        else:
            val = key
        return 1 + (val % (self.table_size - 1))

    def insert(self, key):
        if self.count >= self.table_size:
            raise Exception("Hash table is full. Cannot insert new key.")
        
        pos = self.hash1(key)
        start_pos = pos
        step_size = self.hash2(key)  
        
        while self.table[pos] is not None:
            if self.table[pos] == key:  # Ingen duplikater
                return
            self.collisions += 1
            pos = (pos + step_size) % self.table_size
            
            if pos == start_pos:
                raise Exception("Hash table is full after probing.")
        
        self.table[pos] = key
        self.count += 1
    
    def search(self, key) -> bool:
        pos = self.hash1(key)
        start_pos = pos
        step_size = self.hash2(key)
        
        while self.table[pos] is not None:
            if self.table[pos] == key:
                return True
            pos = (pos + step_size) % self.table_size
            if pos == start_pos:
                break

        return False   

# Testprogram for del 1
print("\n" + "=" * 50)
print("Testprogram for del 1\n")
filename = "navn.txt"

with open(filename, encoding="utf-8") as f:
    num_names = sum(1 for _ in f if _.strip())
table_size = nextprime(int(num_names))

ht = HashTable(table_size=table_size)

with open(filename, encoding="utf-8") as f:
    for line in f:
        navn = line.strip()
        if navn:
            ht.insert(navn)

navn = "Gilianne Kate Alivia,Reyes"
print(f"\nEr {navn} med i faget? {ht.search(navn)}\n")
ht.stats()

# Testprogram for del 2 - Lineær Probing og Dobbel Hashing
print("\n" + "=" * 50)
print("Testprogram for del 2 - Lineær Probing og Dobbel Hashing\n")

def generate_unique_keys(num_keys, step_max=1000):
    keys = []
    current = rd.randint(1, step_max)
    for _ in range(num_keys):
        keys.append(current)
        current += rd.randint(1, step_max)
    rd.shuffle(keys)
    return keys

num_keys = 10_000_000
keys = generate_unique_keys(num_keys)

def measure_time_collisions(ht, keys):
    start_time = time.perf_counter()
    for k in keys:
        ht.insert(k)
    end_time = time.perf_counter()
    return (end_time - start_time), ht.collisions

# Eksperimenter
load_factors = [0.5, 0.8, 0.9, 0.99, 1.0]
table_size = nextprime(num_keys)
for load_factor in load_factors:
    num_insert = int(num_keys * load_factor)

    ht_linear = HashTableLinearProbing(table_size)
    ht_double = HashTableDoubleHashing(table_size)

    time_linear, collisions_linear = measure_time_collisions(ht_linear, keys[:num_insert])
    time_double, collisions_double = measure_time_collisions(ht_double, keys[:num_insert])

    print(f"Lineær Probing ({int(load_factor*100)}%): {time_linear:.3f}s, Collisions: {collisions_linear}")
    print(f"Dobbel Hashing ({int(load_factor*100)}%): {time_double:.3f}s, Collisions: {collisions_double}")
    print("-" * 50)
