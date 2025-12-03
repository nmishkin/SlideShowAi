import os
import ftplib
import argparse
import getpass
import io
from PIL import Image
import pillow_heif
import piexif

# Register HEIC opener
pillow_heif.register_heif_opener()

def connect_ftp(host, user, password, path):
    try:
        print(f"Connecting to FTP {host} as {user}...")
        ftp = ftplib.FTP(host)
        ftp.login(user, password)
        try:
            ftp.cwd(path)
        except ftplib.error_perm:
            print(f"Directory {path} does not exist. Creating it.")
            ftp.mkd(path)
            ftp.cwd(path)
        return ftp
    except Exception as e:
        print(f"FTP Connection Error: {e}")
        return None

def process_image(filepath):
    """
    Reads an image, checks dimensions, resizes if needed, and returns:
    (filename_to_upload, image_bytes)
    Returns (None, None) if the image should be skipped (e.g. portrait).
    """
    filename = os.path.basename(filepath)
    base_name, ext = os.path.splitext(filename)
    ext = ext.lower()
    
    if ext not in ['.jpg', '.jpeg', '.png', '.heic', '.heif']:
        return None, None

    try:
        img = Image.open(filepath)
        width, height = img.size
        
        # 1. Skip Portrait (Taller than wide)
        if height > width:
            print(f"Skipping {filename} (Portrait: {width}x{height})")
            return None, None
            
        # 2. Downres if width > 1280
        if width > 1280:
            print(f"Resizing {filename} ({width}x{height} -> 1280 width)...")
            ratio = 1280 / width
            new_height = int(height * ratio)
            
            img_resized = img.resize((1280, new_height), Image.Resampling.LANCZOS)
            
            output_buffer = io.BytesIO()
            
            # Preserve EXIF using piexif
            exif_bytes = None
            if 'exif' in img.info:
                try:
                    exif_dict = piexif.load(img.info['exif'])
                    # Remove MakerNote to prevent corruption
                    if piexif.ExifIFD.MakerNote in exif_dict['Exif']:
                        del exif_dict['Exif'][piexif.ExifIFD.MakerNote]
                    exif_bytes = piexif.dump(exif_dict)
                except Exception as e:
                    print(f"  - Warning: Failed to process EXIF for {filename}: {e}")

            if exif_bytes:
                img_resized.save(output_buffer, format="JPEG", quality=85, exif=exif_bytes)
            else:
                img_resized.save(output_buffer, format="JPEG", quality=85)
                
            output_buffer.seek(0)
            return f"{base_name}.jpg", output_buffer
        else:
            # No resize needed, upload original file
            # If it's HEIC, we might want to convert it? 
            # The prompt didn't explicitly say to convert everything, but "downres" implies processing.
            # If we don't resize, we just return the original bytes.
            # However, if it's HEIC and we don't resize, the Android app might prefer JPEG.
            # But let's stick to "downres if width > 1280".
            # If it is < 1280, we upload as is.
            with open(filepath, 'rb') as f:
                return filename, io.BytesIO(f.read())

    except Exception as e:
        print(f"Error processing {filename}: {e}")
        return None, None

def sync_local_folder(host, user, password, remote_path, local_dir):
    if not os.path.isdir(local_dir):
        print(f"Error: Local directory '{local_dir}' does not exist.")
        return

    ftp = connect_ftp(host, user, password, remote_path)
    if not ftp:
        return

    # Get existing files on server
    try:
        existing_files = ftp.nlst()
    except:
        existing_files = []

    processed_files = set()

    # Iterate local files
    print(f"Scanning local directory: {local_dir}")
    for root, dirs, files in os.walk(local_dir):
        for file in files:
            filepath = os.path.join(root, file)
            
            # Process image (resize, check portrait, etc)
            upload_filename, image_data = process_image(filepath)
            
            if not upload_filename:
                continue
                
            processed_files.add(upload_filename)
            
            if upload_filename in existing_files:
                print(f"Skipping {upload_filename} (exists)")
                continue
                
            print(f"Uploading {upload_filename}...")
            try:
                ftp.storbinary(f"STOR {upload_filename}", image_data)
            except Exception as e:
                print(f"Failed to upload {upload_filename}: {e}")

    # Delete orphans
    print("\nChecking for files to delete on server...")
    for existing_file in existing_files:
        if existing_file not in processed_files:
            # Be careful not to delete directories if nlst returns them
            # Usually nlst returns simple names.
            # We can try to delete.
            print(f"Deleting {existing_file} (not in local folder)...")
            try:
                ftp.delete(existing_file)
            except Exception as e:
                print(f"Failed to delete {existing_file}: {e}")

    ftp.quit()
    print("Sync complete.")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Sync Local Photos to FTP')
    parser.add_argument('host', help='FTP Server Host')
    parser.add_argument('user', help='FTP Username')
    parser.add_argument('remote_path', help='Remote directory path to store photos')
    parser.add_argument('local_dir', help='Local directory containing photos')
    
    args = parser.parse_args()
    
    password = getpass.getpass(prompt=f"Enter password for {args.user}@{args.host}: ")
    
    sync_local_folder(args.host, args.user, password, args.remote_path, args.local_dir)
