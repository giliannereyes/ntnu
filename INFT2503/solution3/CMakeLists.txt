cmake_minimum_required(VERSION 4.0)
project(solution3)

set(CMAKE_CXX_STANDARD 20)

add_compile_options(-Wall -Wextra)

add_executable(task1-2 circle.cpp circle.hpp circle_client.cpp)
add_executable(task3 commodity.cpp commodity.hpp commodity_client.cpp)
add_executable(task4 task_4.cpp)
