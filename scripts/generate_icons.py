import os
from PIL import Image

def generate_icons(source_path, res_dir):
    if not os.path.exists(source_path):
        print(f"Error: Source image {source_path} not found.")
        return

    img = Image.open(source_path)
    
    # Legacy Mipmap Sizes
    densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }
    
    print("Generating legacy icons...")
    for folder, size in densities.items():
        out_dir = os.path.join(res_dir, folder)
        os.makedirs(out_dir, exist_ok=True)
        
        # Resize
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save ic_launcher.png
        resized.save(os.path.join(out_dir, 'ic_launcher.png'))
        
        # Save ic_launcher_round.png (For now just same image, could mask it)
        resized.save(os.path.join(out_dir, 'ic_launcher_round.png'))
        print(f"  Saved {folder} ({size}x{size})")

    # Adaptive Icon Foreground
    # xxxhdpi is 192px (legacy). Adaptive base is 108dp.
    # 1dp = 4px at xxxhdpi. So 108dp = 432px.
    # Safe zone is 72dp = 288px.
    
    print("Generating adaptive foreground...")
    adaptive_size = 432
    safe_zone = 288
    
    # Create transparent canvas
    foreground = Image.new('RGBA', (adaptive_size, adaptive_size), (0, 0, 0, 0))
    
    # Resize source to fit in safe zone (slightly smaller to be safe, say 260px)
    icon_size = 260
    icon_resized = img.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
    
    # Center it
    offset = (adaptive_size - icon_size) // 2
    foreground.paste(icon_resized, (offset, offset))
    
    # Save to drawable-xxxhdpi (or drawable)
    # Ideally we should have densities for foreground too, but let's put high res in drawable-nodpi or drawable-xxxhdpi
    # Let's put it in drawable-xxxhdpi for best quality scaling down
    drawable_dir = os.path.join(res_dir, 'drawable-xxxhdpi')
    os.makedirs(drawable_dir, exist_ok=True)
    
    foreground.save(os.path.join(drawable_dir, 'ic_launcher_foreground.png'))
    print(f"  Saved adaptive foreground to {drawable_dir}")

if __name__ == '__main__':
    source = '/Users/mishkin/.gemini/antigravity/brain/c6b1455e-1f4e-4bd0-ad4f-c0b5621c2d72/app_icon_1764945859154.png'
    res = '/Users/mishkin/src/SlideShowAi/app/src/main/res'
    generate_icons(source, res)
