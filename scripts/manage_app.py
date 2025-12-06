import argparse
import os
import io
import socket
import json
from PIL import Image, ImageOps
import pillow_heif
import piexif

# Register HEIF opener
pillow_heif.register_heif_opener()

def process_image(filepath):
    """
    Reads an image, checks dimensions, resizes if needed (max width 1280),
    preserves EXIF (sanitized), and returns (filename, bytes).
    """
    try:
        filename = os.path.basename(filepath)
        
        # Load EXIF first to check orientation if needed, but Pillow handles it mostly.
        # However, for the 'Skip Portrait' logic, we need dimensions.
        
        img = Image.open(filepath)
        width, height = img.size
        
        # Check orientation via EXIF to be sure about dimensions?
        # Pillow's ImageOps.exif_transpose handles rotation.
        img = ImageOps.exif_transpose(img)
        width, height = img.size
            
        # Resize if width > 1280
        if width > 1280:
            new_width = 1280
            new_height = int(height * (1280 / width))
            img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
            
        # Handle EXIF
        exif_dict = None
        if "exif" in img.info:
            try:
                exif_dict = piexif.load(img.info["exif"])
                # Remove MakerNote to prevent corruption
                if "Exif" in exif_dict and piexif.ExifIFD.MakerNote in exif_dict["Exif"]:
                    del exif_dict["Exif"][piexif.ExifIFD.MakerNote]
            except Exception:
                pass
                
        # Save to BytesIO
        output = io.BytesIO()
        if exif_dict:
             try:
                exif_bytes = piexif.dump(exif_dict)
                img.save(output, format="JPEG", quality=85, exif=exif_bytes)
             except:
                img.save(output, format="JPEG", quality=85)
        else:
            img.save(output, format="JPEG", quality=85)
            
        return filename, output.getvalue()

    except Exception as e:
        print(f"Error processing {filename}: {e}")
        return None, None

def read_line(sock):
    """Read a line ending with \n from the socket."""
    buf = b""
    while True:
        c = sock.recv(1)
        if not c:
            break
        if c == b'\n':
            break
        buf += c
    return buf.decode('utf-8')

def sync_direct_push(app_host, port, local_dirs):
    print(f"Connecting to Android App at {app_host}:{port}...")
    
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(None) # Disable timeout for large transfers
        try:
            s.connect((app_host, port))
        except Exception as e:
            print(f"Failed to connect: {e}")
            return

        # 1. Get List of Files
        print("Fetching headers...")
        list_cmd = json.dumps({"cmd": "list_files"})
        s.sendall(list_cmd.encode('utf-8') + b'\n')
        
        response_str = read_line(s)
        if not response_str:
            print("Failed to get response from app.")
            return
            
        response = json.loads(response_str)
        if response.get("status") != "ok":
            print(f"Error getting file list: {response.get('message')}")
            return
            
        remote_files = set(response.get("files", []))
        print(f"App has {len(remote_files)} files.")

        # 2. Process Local Files
        processed_files = set()
        
        for local_dir in local_dirs:
            if not os.path.isdir(local_dir):
                print(f"Error: {local_dir} not found.")
                return
                
            print(f"Scanning {local_dir}...")
            for root, dirs, files in os.walk(local_dir):
                for file in files:
                    filepath = os.path.join(root, file)
                    
                    # We only process if we plan to upload OR if we need to mark it as 'kept'
                    # But we don't know the final filename until we process it?
                    # Actually, usually filename matches.
                    # Optimization: Check if filename exists on remote BEFORE processing?
                    # But we might need to re-upload if changed? For now, assume filename unique ID.
                    
                    # To be safe and consistent with previous logic, we process to get the 'clean' filename
                    # (though it's usually just basename).
                    
                    # Let's just use basename for check to speed up?
                    # "process_image" does filtering (portrait), which is important.
                    # So we MUST process.
                    
                    upload_filename, image_data = process_image(filepath)
                    if not upload_filename:
                        continue
                        
                    processed_files.add(upload_filename)
                    
                    if upload_filename in remote_files:
                        print(f"Skipping {upload_filename} (exists)")
                        continue
                        
                    # Upload
                    print(f"Uploading {upload_filename} ({len(image_data)} bytes)...")
                    
                    # Send Command
                    cmd = json.dumps({
                        "cmd": "receive_file",
                        "name": upload_filename,
                        "size": len(image_data)
                    })
                    s.sendall(cmd.encode('utf-8') + b'\n')
                    
                    # Wait for ready
                    resp = json.loads(read_line(s))
                    if resp.get("status") != "ready":
                        print(f"App not ready: {resp.get('message')}")
                        continue
                        
                    # Send Data
                    s.sendall(image_data)
                    
                    # Wait for Ack
                    resp = json.loads(read_line(s))
                    if resp.get("status") != "ok":
                         print(f"Upload failed: {resp.get('message')}")
                    else:
                         print("Success.")

        # 3. Delete Orphans
        print("\nChecking for orphans...")
        for remote_file in remote_files:
            if remote_file not in processed_files:
                print(f"Deleting {remote_file}...")
                cmd = json.dumps({
                    "cmd": "delete_file",
                    "name": remote_file
                })
                s.sendall(cmd.encode('utf-8') + b'\n')
                
                resp = json.loads(read_line(s))
                if resp.get("status") != "ok":
                    print(f"Failed to delete: {resp.get('message')}")
                    
        print("Sync Complete.")

def show_db(app_host, port, db_name):
    print(f"Connecting to Android App at {app_host}:{port}...")
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((app_host, port))
            
            cmd = json.dumps({"cmd": "get_db", "db": db_name})
            s.sendall(cmd.encode('utf-8') + b'\n')
            
            response_str = read_line(s)
            if response_str:
                response = json.loads(response_str)
                if response.get("status") == "ok":
                    print(json.dumps(response.get("data"), indent=2))
                else:
                    print(f"Error: {response.get('message')}")
            else:
                print("No response from app.")
    except Exception as e:
        print(f"Connection failed: {e}")

def clear_db(app_host, port, db_name):
    print(f"Connecting to Android App at {app_host}:{port}...")
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((app_host, port))
            
            cmd = json.dumps({"cmd": "clear_db", "db": db_name})
            s.sendall(cmd.encode('utf-8') + b'\n')
            
            response_str = read_line(s)
            if response_str:
                response = json.loads(response_str)
                if response.get("status") == "ok":
                    print(f"Success: {response.get('message')}")
                else:
                    print(f"Error: {response.get('message')}")
            else:
                print("No response from app.")
    except Exception as e:
        print(f"Connection failed: {e}")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='SlideShowAi Management Tool')
    subparsers = parser.add_subparsers(dest='command', help='Command to run')
    
    # Sync Command
    sync_parser = subparsers.add_parser('sync', help='Sync photos to the app')
    sync_parser.add_argument('app_host', help='Android App Hostname/IP')
    sync_parser.add_argument('local_dirs', nargs='+', help='Local directories containing photos')
    sync_parser.add_argument('--port', type=int, default=4000, help='App TCP Port (default 4000)')
    
    # Show DB Command
    show_parser = subparsers.add_parser('show-db', help='Show database contents')
    show_parser.add_argument('app_host', help='Android App Hostname/IP')
    show_parser.add_argument('--port', type=int, default=4000, help='App TCP Port (default 4000)')
    show_parser.add_argument('--db', required=True, choices=['location', 'history'], help='Database to show')
    
    # Clear DB Command
    clear_parser = subparsers.add_parser('clear-db', help='Clear database contents')
    clear_parser.add_argument('app_host', help='Android App Hostname/IP')
    clear_parser.add_argument('--port', type=int, default=4000, help='App TCP Port (default 4000)')
    clear_parser.add_argument('--db', required=True, choices=['location', 'history'], help='Database to clear')
    
    args = parser.parse_args()
    
    if args.command == 'sync':
        sync_direct_push(args.app_host, args.port, args.local_dirs)
    elif args.command == 'show-db':
        show_db(args.app_host, args.port, args.db)
    elif args.command == 'clear-db':
        # Confirm action
        confirm = input(f"Are you sure you want to CLEAR the {args.db} database? (yes/no): ")
        if confirm.lower() == 'yes':
             clear_db(args.app_host, args.port, args.db)
        else:
            print("Aborted.")
    else:
        parser.print_help()
