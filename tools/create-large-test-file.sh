#!/bin/bash

# Check if the input file and number of repetitions are provided
if [ "$#" -ne 3 ]; then
    printf "\033[0;31mUsage: $0 <filename> <number_of_repetitions>\033[0m\n"
    exit 1
fi

input_file=$1
repetitions=$2
output_file=$3

# Check if the input file exists
if [ ! -f "$input_file" ]; then
    printf "\033[0;31mError: File '$input_file' not found.\033[0m\n"
    exit 1
fi

# Clear the output file if it already exists
> "$output_file"

# Concatenate the file with itself specified number of times
for (( i=0; i<repetitions; i++ ))
do
    cat "$input_file" >> "$output_file"
done

printf "\033[0;32mSuccessfully created '$output_file'.\033[0m\n"
