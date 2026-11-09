# 🚀 Player Totems — Modrinth & CurseForge Release Package

---

## 🖼️ Visual Assets & Storefront Info

### Current Primary Icon (`assets/playertotems/icon.png`)
- **Dimensions:** 1024 × 1024 PNG (Crisp 4K/Retina ready).
- **Visual Composition:** An authentic 3D GUI item render of the custom player totem featuring vibrant cyan horns, glowing eyes, and subtle front lighting, set against a deep obsidian gradient with a soft ambient cyan horn highlight. 100% compliant with Modrinth and CurseForge guidelines (no AI-generated art).

### Alternative / Storefront Banner Concepts:
1. **The Duality (Steve & Alex):** A split banner showing Classic (4px) Steve on the left and Slim (3px) Alex on the right, visualising the automatic arm model detection.
2. **The Hall of Champions:** A cozy wooden base wall lined with glowing item frames, each holding totems with different multiplayer friends' skins, highlighting the server item frame memory feature.
3. **The Anvil Inscription:** An anvil workbench with a hammer and a glowing totem with the nametag hover text floating above it.

---

## 📝 Modrinth & CurseForge Ready-to-Copy Description

*(Copy everything below this line into your Modrinth / CurseForge project description editor)*

***

# 🌟 Player Totems — Dynamic 3D Player Skins for Totems of Undying!

> **Tired of static golden totems or clunky resource packs that make every totem look the same?**
> **Player Totems** completely re-engineers the Totem of Undying into a personalized 3D guardian spirit sculpted in the exact likeness of you and your friends!

![Player Totems Showcase](Gruppenfoto.png)

---

### ✨ Features at a Glance

* 👤 **Dynamic In-Hand Skins:** When you hold a totem, it shows your skin in full 3D, including outer layer details (hats, hair, coats, 3D armor details). When your friend holds one, you see *their* skin!
* 🏷️ **Anvil Player Binding:** Rename any Totem of Undying in an anvil to any player's username (e.g. `Technoblade` or your best friend) to **permanently lock** that totem to their skin!
* 🌈 **Easter Eggs & Modifiers (`#jeb_` & `#dinnerbone`):** Tag any totem with `#jeb_` for a luminous animated rainbow border outlining your true skin, or `#dinnerbone` / `#grumm` to flip it upside-down! (e.g. `Technoblade#jeb_` or `Technoblade#jeb_#dinnerbone`!)
* 🎒 **Dynamic Hotbar & Inventory Sprites:** Renamed totems display their custom player skins directly in your hotbar, inventory slots, chests, and crafting tables!
* 🖼️ **Item Frame Trophy Displays:** Unrenamed totems placed in Item Frames remember who hung them on the wall. Create epic Hall of Fame walls, shrines, or clan bases!
* 📐 **Steve (4px) & Alex (3px) Auto-Detection:** Seamlessly identifies whether a player uses Classic or Slim models and reshapes the arm geometry with zero texture bleeding.
* ⚡ **Zero Lag:** Skin downloads happen seamlessly in the background so your game stays completely smooth.
* 🛡️ **Lag-Free Safety:** Built-in safeguards prevent lag and protect your game from slowing down.
* 🌐 **Server & Client Hybrid:** Works 100% on the client side when playing on vanilla servers! When also installed on a Fabric server, it unlocks persistent item frame storage and synchronized anvil binding for all players.

---

### 🎮 How to Use

#### 1. Normal Gameplay (Contextual Rendering)
Simply hold any unrenamed Totem of Undying:
* **In your hand / inventory / dropped on the floor:** Displays your skin.
* **Held by another player:** Displays that player's skin in third-person view.

#### 2. Anvil Renaming (Permanent Binding)
1. Place a **Totem of Undying** in an **Anvil**.
2. Type any player's valid username in the text box.
3. Complete the rename.
4. The totem is now permanently bound! It will display that player's skin no matter who holds it, drops it, or puts it in a chest.
*(Clear the name in an anvil to return it to an ordinary dynamic totem!)*

#### 3. Easter Egg Modifiers
* **`<Username>#jeb_`**: Displays that player's authentic skin surrounded by a vibrant, luminous animated rainbow border!
* **`<Username>#dinnerbone`** (or `#grumm`): Renders that player's skin upside-down!
* **`<Username>#jeb_#dinnerbone`**: Combines both modifiers simultaneously!
* **`#jeb_`** or **`#dinnerbone`**: Adds the effect to your own/contextual totem skin without binding a specific username.
* **`jeb_`** or **`Dinnerbone`**: Standalone classic vanilla easter eggs.

#### 4. Wall Frames & Displays
Place an unrenamed totem in an **Item Frame** on a server with Player Totems installed — the frame will permanently display your skin to anyone walking by!

---

### 📦 Installation & Dependencies

| Requirement | Supported Version |
| :--- | :--- |
| **Minecraft** | `26.3` |
| **Mod Loader** | `Fabric Loader >= 0.18.0` |
| **Java** | `Java 25+` |

*Client & Server Compatible:*
- **Client-only:** Fully works on vanilla / multiplayer servers!
- **Server:** Optional, enables synchronized Item Frame placer memory and server-wide anvil binding.

---

### 💡 FAQ

**Q: Does this require a resource pack?**
A: **No!** Player Totems uses native Fabric rendering hooks and fetches skins dynamically. No resource packs required.

**Q: Can I use this with shaders like Iris or Sodium?**
A: **Yes!** Player Totems works right out of the box with modern optimization and shader mods like Sodium and Iris.

**Q: What happens if a player has custom skin layers (hats, jackets)?**
A: All 3D outer skin layers are fully supported so your character's clothes, hats, and hair look great!

---

### 📄 Author, Credits & License

- **Created by:** DjElectroBox
- **Credits & Thanks:** Froeloff (original inspiration & testing)
- **License:** Licensed under the permissive **MIT License**. You are free to include this mod in any modpack!
