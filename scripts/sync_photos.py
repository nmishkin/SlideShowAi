import os
import pickle
import json
import time
import ftplib
import argparse
import getpass
import requests
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request

# New Picker API Scope
SCOPES = ['https://www.googleapis.com/auth/photospicker.mediaitems.readonly']

def get_credentials():
    creds = None
    token_file = 'token.json'
    
    if os.path.exists(token_file):
        with open(token_file, 'rb') as token:
            creds = pickle.load(token)
            
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not os.path.exists('client_secret.json'):
                print("Error: client_secret.json not found.")
                return None

            flow = InstalledAppFlow.from_client_secrets_file(
                'client_secret.json', SCOPES)
            creds = flow.run_local_server(port=8080)
            
        with open(token_file, 'wb') as token:
            pickle.dump(creds, token)
    return creds

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

def sync_photos_picker(host, user, password, path):
    ftp = connect_ftp(host, user, password, path)
    if not ftp:
        return

    creds = get_credentials()
    if not creds:
        return

    # Use requests with auth
    session = requests.Session()
    session.headers.update({'Authorization': f'Bearer {creds.token}'})

    # 1. Create a Picker Session
    print("Creating Photo Picker session...")
    create_url = 'https://photospicker.googleapis.com/v1/sessions'
    response = session.post(create_url, json={})
    
    if response.status_code != 200:
        print(f"Error creating session: {response.text}")
        return

    picker_data = response.json()
    picker_uri = picker_data.get('pickerUri')
    session_id = picker_data.get('id')

    print("\n" + "="*60)
    print("ACTION REQUIRED: Open the following URL in your browser:")
    print(f"\n{picker_uri}\n")
    print("Select the photos you want to sync, then click 'Done'.")
    print("="*60 + "\n")

    # 2. Poll for completion
    print("Waiting for you to select photos...")
    media_items = []
    
    while True:
        time.sleep(2) # Poll every 2 seconds
        poll_url = f'https://photospicker.googleapis.com/v1/sessions/{session_id}'
        poll_resp = session.get(poll_url)
        
        if poll_resp.status_code != 200:
            print(f"Error polling session: {poll_resp.text}")
            return

        poll_data = poll_resp.json()
        # Check if mediaItems are present (user finished selection)
        # Note: The API might return items incrementally or wait for 'Done'.
        # Usually, the session remains active. We check if 'mediaItems' is in response.
        # But wait, how do we know the user is *done*?
        # The Picker API doesn't have a "status" field in the session object in v1.
        # It relies on the client polling. If the user picked items, they appear.
        # We might need to ask the user to press Enter in the script, 
        # OR we can detect when items appear. 
        # However, user might pick 1, then pick another.
        # Let's wait for at least one item, then give a grace period or ask user to confirm.
        
        # Actually, standard flow is: User clicks Done in web UI.
        # Does the API reflect "Done"?
        # Documentation says: "The session will contain the media items selected by the user."
        # It doesn't explicitly signal "Done".
        
        # Check if mediaItems are present
        current_items = poll_data.get('mediaItems', [])
        if current_items:
            print(f"Detected {len(current_items)} items selected...")
            # We could break here, but user might be selecting more.
            # Let's just notify.
        
        # Non-blocking check for input is hard in pure Python without curses/threads.
        # So we will just rely on the user pressing Enter below.
        break 

    input("Press Enter here AFTER you have finished selecting photos in the browser...")
    
    # Fetch final state
    poll_url = f'https://photospicker.googleapis.com/v1/sessions/{session_id}'
    final_resp = session.get(poll_url)
    final_data = final_resp.json()
    
    # DEBUG: Print full response
    print(f"DEBUG: Final API Response: {json.dumps(final_data, indent=2)}")
    
    media_items = final_data.get('mediaItems', [])

    # If mediaItems is empty but mediaItemsSet is true, try fetching from sub-resource
    if not media_items and final_data.get('mediaItemsSet'):
        print("mediaItemsSet is true. Fetching items from /mediaItems endpoint...")
        # Correct endpoint: https://photospicker.googleapis.com/v1/mediaItems?sessionId=...
        list_items_url = 'https://photospicker.googleapis.com/v1/mediaItems'
        list_resp = session.get(list_items_url, params={'sessionId': session_id, 'pageSize': 100})
        
        if list_resp.status_code == 200:
            list_data = list_resp.json()
            print(f"DEBUG: List Items Response: {json.dumps(list_data, indent=2)}")
            media_items = list_data.get('mediaItems', [])
        else:
            print(f"Failed to list media items: {list_resp.status_code} {list_resp.text}")

    if not media_items:
        print("No photos selected (mediaItems field is empty).")
        return

    print(f"Found {len(media_items)} selected photos.")
    
    # Get existing files
    try:
        existing_files = ftp.nlst()
    except:
        existing_files = []

    # 3. Download and Upload
    for item in media_items:
        media_file = item.get('mediaFile', {})
        filename = media_file.get('filename')
        base_url = media_file.get('baseUrl')
        metadata = media_file.get('mediaFileMetadata', {})
        
        if not filename or not base_url:
            continue
            
        # Check dimensions
        width = int(metadata.get('width', 0))
        height = int(metadata.get('height', 0))
        
        if width == 0 or height == 0:
            print(f"Skipping {filename} (unknown dimensions)")
            continue
            
        # 1. Skip Portrait (Taller than wide)
        if height > width:
            print(f"Skipping {filename} (Portrait: {width}x{height})")
            continue
            
        # 2. Downres if width > 1280
        if width > 1280:
            print(f"Resizing {filename} ({width}x{height} -> 1280 width)...")
            download_url = f"{base_url}=w1280"
            # Google Photos resizing usually returns JPEG. Update extension.
            base_name = os.path.splitext(filename)[0]
            filename = f"{base_name}.jpg"
        else:
            download_url = f"{base_url}=d" # Download original
            
        if filename in existing_files:
            print(f"Skipping {filename} (exists)")
            continue

        print(f"Downloading {filename}...")
        
        try:
            # Stream download
            with session.get(download_url, stream=True) as r:
                r.raise_for_status()
                print(f"Uploading {filename}...")
                ftp.storbinary(f"STOR {filename}", r.raw)
        except Exception as e:
            print(f"Failed to transfer {filename}: {e}")

    ftp.quit()
    print("Sync complete.")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Sync Google Photos to FTP')
    parser.add_argument('host', help='FTP Server Host')
    parser.add_argument('user', help='FTP Username')
    parser.add_argument('path', help='Remote directory path to store photos')
    
    args = parser.parse_args()
    
    password = getpass.getpass(prompt=f"Enter password for {args.user}@{args.host}: ")
    
    sync_photos_picker(args.host, args.user, password, args.path)
