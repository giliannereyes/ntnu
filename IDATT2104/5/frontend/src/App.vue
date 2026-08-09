<script setup lang="ts">
import { ref } from 'vue'

type RunResponse = {
  stdout: string
  stderr: string
  exit_code: number
  timed_out: boolean
}

type RunRequest = {  
  language: 'cpp'
  source_code: string
  timeout_seconds: number
}

const code = ref(`#include <iostream>
using namespace std;

int main() {
  cout << "Hello World" << endl;
  return 0;
}
`)

const output = ref('')
const isRunning = ref(false)
const timeoutSeconds = ref(3)
const backendUrl = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8000'

async function runCode() {
  isRunning.value = true
  output.value = 'Compiling and running...\n'

  try {
    const request: RunRequest = {
      language: 'cpp',
      source_code: code.value,
      timeout_seconds: timeoutSeconds.value,
    }

    const res = await fetch(`${backendUrl}/run`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })

    if (!res.ok) {
      const text = await res.text()
      output.value += `Request failed (${res.status}): ${text}`
      return
    }

    const data: RunResponse = await res.json()

    const lines: string[] = []
    if (data.stdout.trim()) {
      lines.push('STDOUT:', data.stdout.trimEnd())
    }
    if (data.stderr.trim()) {
      lines.push('STDERR:', data.stderr.trimEnd())
    }
    lines.push(`Exit code: ${data.exit_code}`)
    if (data.timed_out) lines.push('Execution timed out.')

    output.value += lines.join('\n')
  } catch (err) {
    output.value += `Error: ${String(err)}`
  } finally {
    isRunning.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-slate-100 p-4 text-slate-900 md:p-8">
    <section class="mx-auto flex w-full max-w-5xl flex-col gap-4 rounded-xl border border-slate-300 bg-white p-4 shadow-sm md:p-6">
      <header class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <h1 class="text-xl font-semibold">C++ Compile & Run</h1>
        <div class="flex items-center gap-2 text-sm">
          <label for="timeout" class="font-medium">Timeout (s)</label>
          <input
            id="timeout"
            v-model.number="timeoutSeconds"
            type="number"
            min="1"
            max="8"
            class="w-16 rounded-md border border-slate-300 px-2 py-1"
          />
        </div>
      </header>

      <div>
        <label class="mb-1 block text-sm font-medium">main.cpp</label>
        <textarea
          v-model="code"
          class="h-72 w-full resize-y rounded-md border border-slate-300 bg-slate-50 p-3 font-mono text-sm outline-none focus:border-blue-500"
        />
      </div>

      <div>
        <button
          class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
          :disabled="isRunning"
          @click="runCode"
        >
          {{ isRunning ? 'Running...' : 'Compile and Run' }}
        </button>
      </div>

      <div>
        <h2 class="mb-1 text-sm font-semibold">Output</h2>
        <pre class="min-h-32 whitespace-pre-wrap rounded-md border border-slate-300 bg-slate-900 p-3 font-mono text-sm text-slate-100">{{ output }}</pre>
      </div>
    </section>
  </main>
</template>
