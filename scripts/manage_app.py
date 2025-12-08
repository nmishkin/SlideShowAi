import argparse
import os
import io
import socket
import json
from PIL import Image, ImageOps
import pillow_heif
import piexif
import datetime

# Register HEIF opener
pillow_heif.register_heif_opener()

def get_device_resolution(app_host, port):
    print(f"Fetching device resolution from {app_host}:{port}...")
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((app_host, port))
            cmd = json.dumps({"cmd": "get_device_info"})
            s.sendall(cmd.encode('utf-8') + b'\n')
            
            response_str = read_line(s)
            if response_str:
                response = json.loads(response_str)
                if response.get("status") == "ok":
                    width = response.get("width")
                    height = response.get("height")
                    print(f"Device Resolution: {width}x{height}")
                    return width, height
                else:
                    print(f"Error getting info: {response.get('message')}")
    except Exception as e:
        print(f"Failed to get device info: {e}")
    return 1920, 1080 # Fallback

def process_image(filepath, target_width, target_height):
    """
    Reads an image, handles orientation, resizes (aspect fill) and crops 
    to exactly match target_width x target_height.
    Returns (filename, bytes).
    """
    try:
        filename = os.path.basename(filepath)
        
        img = Image.open(filepath)
        
        # 1. Handle EXIF Orientation
        img = ImageOps.exif_transpose(img)
        
        # 2. Determine Smart Target Dimensions
        # We need to map the image's orientation to the device's screen dimensions.
        img_w, img_h = img.size
        
        # Determine if image is landscape or portrait
        is_img_landscape = img_w >= img_h
        
        # Determine device main dimensions
        dev_max = max(target_width, target_height)
        dev_min = min(target_width, target_height)
        
        # Set target dimensions based on image orientation
        if is_img_landscape:
            final_w = dev_max
            final_h = dev_min
        else:
            final_w = dev_min
            final_h = dev_max
            
        # 3. Aspect Fill Resize
        # Calculate scale to cover
        scale_w = final_w / img_w
        scale_h = final_h / img_h
        scale = max(scale_w, scale_h)
        
        new_w = int(img_w * scale)
        new_h = int(img_h * scale)
        
        img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
        
        # 4. Center Crop
        left = (new_w - final_w) / 2
        top = (new_h - final_h) / 2
        right = (new_w + final_w) / 2
        bottom = (new_h + final_h) / 2
        
        img = img.crop((left, top, right, bottom))
            
        # 5. Handle EXIF
        # We will try to preserve the original EXIF block exactly as is.
        # This prevents piexif from potentially creating incompatible structures for Android.
        # Drawback: Internal thumbnail/MakerNote might be stale, but Location should be readable.
        
        exif_bytes = img.info.get("exif")
        
        # Save to BytesIO
        output = io.BytesIO()
        if exif_bytes:
             try:
                img.save(output, format="JPEG", quality=85, exif=exif_bytes)
             except Exception as e:
                print(f"Warning: Failed to save with EXIF: {e}")
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
    
    # 0. Get Device Resolution
    dev_w, dev_h = get_device_resolution(app_host, port)
    
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
                    
                    # Basic extension check to skip non-images early
                    if not file.lower().endswith(('.jpg', '.jpeg', '.png', '.webp', '.heic')):
                         continue
                    
                    # Process with Smart Resize
                    upload_filename, image_data = process_image(filepath, dev_w, dev_h)
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
                    data = response.get("data")
                    if db_name == "history":
                         # Print Table Header
                        print(f"{'File Name':<40} | {'Last Shown (Local Time)':<30}")
                        print("-" * 75)
                        for item in data:
                            filename = item.get("fileName")
                            ts = item.get("lastShown")
                            if ts and isinstance(ts, (int, float)):
                                dt = datetime.datetime.fromtimestamp(ts / 1000.0) # Local time by default
                                ts_str = dt.strftime("%Y-%m-%d %H:%M:%S")
                            else:
                                ts_str = str(ts)
                            print(f"{filename:<40} | {ts_str:<30}")
                    else:
                        print(json.dumps(data, indent=2))
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

def analyze_orientation(local_dirs):
    print("Analyzing photo orientation...")
    total = 0
    landscape = 0
    portrait = 0
    skipped = 0
    
    for local_dir in local_dirs:
        if not os.path.isdir(local_dir):
            print(f"Warning: {local_dir} not found.")
            continue
            
        for root, dirs, files in os.walk(local_dir):
            for file in files:
                filepath = os.path.join(root, file)
                
                # Basic extension check (optional but speeds things up)
                if not file.lower().endswith(('.jpg', '.jpeg', '.png', '.webp', '.heic')):
                    continue

                try:
                    img = Image.open(filepath)
                    img = ImageOps.exif_transpose(img)
                    width, height = img.size
                    
                    total += 1
                    if width >= height:
                        landscape += 1
                    else:
                        portrait += 1
                except Exception:
                    skipped += 1
                    
    print("\nOrientation Report")
    print("-" * 30)
    print(f"Total Photos: {total}")
    print(f"Landscape:    {landscape} ({landscape/total*100:.1f}%)" if total else "Landscape:    0")
    print(f"Portrait:     {portrait} ({portrait/total*100:.1f}%)" if total else "Portrait:     0")
    print(f"Skipped/Error: {skipped}")

def delete_all_photos(app_host, port):
    print(f"Connecting to Android App at {app_host}:{port}...")
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((app_host, port))
            
            cmd = json.dumps({"cmd": "delete_all_files"})
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

    # Report Orientation Command
    report_parser = subparsers.add_parser('report-orientation', help='Report count of landscape/portrait photos')
    report_parser.add_argument('local_dirs', nargs='+', help='Local directories containing photos')
    
    # Delete All Command
    delete_all_parser = subparsers.add_parser('delete-all', help='Delete ALL photos and data from app')
    delete_all_parser.add_argument('app_host', help='Android App Hostname/IP')
    delete_all_parser.add_argument('--port', type=int, default=4000, help='App TCP Port (default 4000)')

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
    elif args.command == 'report-orientation':
        analyze_orientation(args.local_dirs)
    elif args.command == 'delete-all':
        # Confirm action
        print("WARNING: This will delete ALL photos and database entries from the Android app.")
        confirm = input("Are you absolutely sure? (yes/no): ")
        if confirm.lower() == 'yes':
             delete_all_photos(args.app_host, args.port)
        else:
            print("Aborted.")
    else:
        parser.print_help()
