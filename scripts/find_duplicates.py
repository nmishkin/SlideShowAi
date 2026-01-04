import argparse
import os
from pathlib import Path
from collections import defaultdict

def find_duplicates(directories):
    # Dictionary to store mapping: stem -> List[(path, size)]
    # "stem" is the name to the left of the last dot
    duplicates = defaultdict(list)
    
    for dir_path in directories:
        path = Path(dir_path)
        if not path.is_dir():
            print(f"Warning: {dir_path} is not a directory or does not exist. Skipping.")
            continue
            
        for file in path.iterdir():
            if file.is_file():
                # Filter to only consider extensions supported by the sync operation
                if not file.name.lower().endswith(('.jpg', '.jpeg', '.png', '.webp', '.heic')):
                    continue

                # stem is already part of Path, but as per user request 
                # "to the left of the last dot", we'll be explicit
                name = file.name
                last_dot_index = name.rfind('.')
                if last_dot_index == -1:
                    stem = name
                else:
                    stem = name[:last_dot_index]
                
                size = file.stat().st_size
                duplicates[stem].append((file.absolute(), size))

    # Filter for entries with more than one file
    results = {stem: files for stem, files in duplicates.items() if len(files) > 1}
    
    if not results:
        print("No duplicate filenames found.")
        return

    print(f"Found {len(results)} groups of duplicate filenames:\n")
    
    for stem in sorted(results.keys()):
        files = results[stem]
        sizes = [f[1] for f in files]
        
        # Check if all sizes are the same
        same_size = all(s == sizes[0] for s in sizes)
        status = "SAME SIZE" if same_size else "DIFFERENT SIZES"
        
        print(f"--- Group: '{stem}' [{status}] ---")
        for p, s in files:
            print(f"  {p} ({s} bytes)")
        print()

def main():
    parser = argparse.ArgumentParser(description="Find duplicate filenames across directories (ignoring extensions) and compare sizes.")
    parser.add_argument("directories", nargs="+", help="One or more directories to scan.")
    
    args = parser.parse_args()
    find_duplicates(args.directories)

if __name__ == "__main__":
    main()
