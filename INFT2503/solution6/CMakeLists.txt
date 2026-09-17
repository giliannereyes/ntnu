cmake_minimum_required(VERSION 3.10)
project(solution6)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++1y -pthread -Wall -Wextra")

add_executable(task1 task_1.cpp)

find_package(Boost 1.54.0 REQUIRED)
include_directories(${Boost_INCLUDE_DIR})

add_executable(task2 task_2.cpp)
target_link_libraries(task2 ${Boost_LIBRARIES})