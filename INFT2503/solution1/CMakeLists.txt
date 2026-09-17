cmake_minimum_required(VERSION 3.10)
project(solution1)

set(CMAKE_CXX_STANDARD 20)
add_compile_options(-Wall -Wextra)

add_executable(task_a task_a.cpp)
add_executable(task_b task_b.cpp)

configure_file(temperatures.txt temperatures.txt COPYONLY)
