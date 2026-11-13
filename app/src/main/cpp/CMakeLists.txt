cmake_minimum_required(VERSION 3.22.1)
project(skachay_runtime LANGUAGES CXX)
add_library(skachay_runtime SHARED runtime.cpp)
target_compile_features(skachay_runtime PRIVATE cxx_std_17)
