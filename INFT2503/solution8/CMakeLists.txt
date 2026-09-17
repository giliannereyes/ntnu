cmake_minimum_required(VERSION 3.10)
project(solution8)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++1y -pthread -Wall -Wextra")

add_executable(task1 task_1.cpp)
add_executable(task2 pair.cpp pair_client.cpp)
