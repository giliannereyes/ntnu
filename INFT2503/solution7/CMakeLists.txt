cmake_minimum_required(VERSION 3.10)
project(solution7)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++1y -pthread -Wall -Wextra")

add_executable(task1 fraction_client.cpp fraction.cpp fraction.hpp)
add_executable(task2 set.cpp set.hpp
        set_client.cpp)
