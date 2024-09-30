def filter_log_file(file_path, filter_number):
    # Open the log file
    with open(file_path, 'r') as file:
        # Read all lines from the file
        lines = file.readlines()
    
    # Filter lines based on the specified number
    filtered_lines = [line for line in lines if line.startswith(f'({filter_number})')]
    
    # Return the filtered lines
    return filtered_lines

def main():
    # Prompt user for the log file path
    file_path = "log.txt"
    
    # Prompt user for the x value
    while True:
        try:
            filter_number = int(input("Filter by: "))
            break
        except ValueError:
            print("Invalid input. Please enter a valid number.")
    
    # Get the filtered lines
    filtered_lines = filter_log_file(file_path, filter_number)
    
    # Print the filtered lines
    print(f"Filtered lines for ({filter_number}):")
    for line in filtered_lines:
        print(line.strip())

if __name__ == "__main__":
    main()
