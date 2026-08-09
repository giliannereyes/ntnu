from collections import defaultdict, deque

class Graph: 
    def __init__(self, filename):
        self.n = 0 # vertices 
        self.m = 0 # edges 
        self.adj = defaultdict(list) # neighbor list
        self.load_from_file(filename)

    def load_from_file(self, filename):
        with open(filename, encoding="utf-8") as f:
            self.n, self.m = map(int, f.readline().strip().split())
            for line in f:
                from_node, to_node = line.strip().split()
                if from_node and to_node:
                    from_node, to_node = int(from_node), int(to_node)
                    self.adj[from_node].append(to_node)

    def bfs(self, start_node):
        dist = {i: None for i in range(self.n)}
        pred = {i: None for i in range(self.n)}
        dist[start_node] = 0

        q = deque([start_node])
        while q:
            u = q.popleft()
            for v in self.adj[u]: 
                if dist[v] is None:
                    dist[v] = dist[u] + 1
                    pred[v] = u
                    q.append(v)

        return dist, pred

    def has_cycle(self):
        state = [0] * self.n  

        for start in range(self.n):
            if state[start] != 0:
                continue

            stack = [(start, iter(self.adj[start]))]
            state[start] = 1  

            while stack:
                node, neighbors = stack[-1]
                try:
                    neighbor = next(neighbors)
                    if state[neighbor] == 1:
                        return True
                    elif state[neighbor] == 0:
                        state[neighbor] = 1
                        stack.append((neighbor, iter(self.adj[neighbor])))
                except StopIteration:
                    state[node] = 2
                    stack.pop()

        return False


    def topological_sort(self):
        if self.has_cycle():
            print("Graph has a cycle and cannot be topologically sorted.")
            return
        
        in_degree = defaultdict(int)
        for from_node in self.adj:
            for to_node in self.adj[from_node]:
                in_degree[to_node] += 1

        zero_in_degree = [node for node in self.adj if in_degree[node] == 0]
        topological_order = []

        while zero_in_degree:
            node = zero_in_degree.pop()
            topological_order.append(node)
            for neighbor in self.adj[node]:
                in_degree[neighbor] -= 1
                if in_degree[neighbor] == 0:
                    zero_in_degree.append(neighbor)

        return topological_order

# Test
file = "ø5g7.txt"   # file 
start_node = 5 # starting node for BFS

g = Graph(file)
dist, pred = g.bfs(start_node)
node_count = g.n

print(f"\nGraph {file}, nodes = {g.n}, edges = {g.m}")
print(f"Breadth-first Search (BFS) from node {start_node}:\n")
print(f"{'Node':<5} {'Predecessor':<12} {'Distance from ' + str(start_node)}")
for i in range(node_count):
    p = '-' if pred[i] is None else pred[i]
    d = 'inf' if dist[i] is None else dist[i]
    print(f"{i:<5} {str(p):<12} {d}")

print(f"\nTopological sort order: {g.topological_sort()}")

