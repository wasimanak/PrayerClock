
import sys

def check_braces(filename):
    try:
        with open(filename, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except Exception as e:
        print(f"Error reading file: {e}")
        return

    balance = 0
    stack = []
    
    # Simple parser that ignores strings/comments would be better, but let's try a rough one first
    # Or strict char by char
    
    in_block_comment = False
    
    for line_num, line in enumerate(lines, 1):
        i = 0
        while i < len(line):
            char = line[i]
            
            # Handle comments
            if not in_block_comment:
                if char == '/' and i + 1 < len(line):
                    if line[i+1] == '/': # Line comment
                        break
                    elif line[i+1] == '*': # Block comment start
                        in_block_comment = True
                        i += 1
                elif char == '"': # String start
                    # Skip to end of string
                    i += 1
                    while i < len(line):
                        if line[i] == '"' and line[i-1] != '\\':
                            break
                        i += 1
                elif char == "'": # Char start
                    # Skip to end of char
                    i += 1
                    while i < len(line):
                        if line[i] == "'" and line[i-1] != '\\':
                            break
                        i += 1
                elif char == '{':
                    balance += 1
                    stack.append(line_num)
                elif char == '}':
                    balance -= 1
                    if len(stack) > 0:
                        stack.pop()
                    else:
                        print(f"Excess closing brace at line {line_num}")
                        return
                    
            elif in_block_comment:
                if char == '*' and i + 1 < len(line) and line[i+1] == '/':
                    in_block_comment = False
                    i += 1
            
            i += 1

    if balance != 0:
        print(f"Brace imbalance! Final balance: {balance}")
        print(f"Unclosed braces opened at lines: {stack[:10]} ...")
    else:
        print("Braces are balanced.")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        check_braces(sys.argv[1])
    else:
        print("Usage: python check_braces.py <filename>")
