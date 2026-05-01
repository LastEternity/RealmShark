# RealmShark ↔ Tomato - Unified Build Environment

**Location**: `<repo-root>/`

This is a unified development environment for the RealmShark ↔ Tomato integration project. Both applications share the same Git repository but are built independently from separate working directories.

---

## 📁 Folder Structure

```
<repo-root>/
│
├── .git/                          ← Shared Git repository
│   └── (realmshark & tomato_integration branches)
│
├── RealmShark/                    ← Backend (Packet Sniffer)
│   ├── src/                       ← Java source code
│   ├── build/                     ← Build output
│   ├── libs/                      ← Dependencies (auto-populated)
│   ├── build.gradle               ← Build configuration
│   └── gradlew                    ← Gradle wrapper
│
├── Tomato/                        ← Frontend (GUI with 11 tabs)
│   ├── src/                       ← Java source code
│   ├── build/                     ← Build output
│   ├── libs/                      ← Dependencies (auto-populated)
│   ├── build.gradle               ← Build configuration
│   └── gradlew                    ← Gradle wrapper
│
├── rotmgppebot/                   ← Python Discord Bot (separate project)
│   ├── slash_commands/
│   ├── utils/
│   └── main.py
│
├── build-all.sh                   ← Full build script (recommended)
├── build-quick.sh                 ← Quick parallel build
├── run.sh                         ← Launch applications
├── README.md                      ← This file
├── INTEGRATION_SUMMARY.md         ← Integration details
├── BUILD_STRUCTURE.md             ← Build organization
├── STATUS.md                      ← Project status
│
├── loot_records.json
├── teams.json
└── [other files]
```

---

## 🚀 Quick Start

### 1. Build Everything
```bash
cd <repo-root>
./build-all.sh
```

This will:
- ✅ Build RealmShark backend
- ✅ Create dependencies folder
- ✅ Copy JAR to both projects
- ✅ Build Tomato frontend

**Output**:
- `RealmShark/build/libs/RealmShark-v1.2.2.jar` (3.2 MB)
- `Tomato/build/libs/PPE-Sniffer-v1.0.jar` (8.7 MB)

### 2. Run Applications

**Run Tomato (with 11 tabs)**:
```bash
./run.sh tomato
```

**Run RealmShark (backend only)**:
```bash
./run.sh realmshark
```

**Run both**:
```bash
./run.sh both
```

---

## 📝 Build Scripts

### `build-all.sh` (Comprehensive)
**Purpose**: Complete build with detailed logging
**Features**:
- Verifies folder structure
- Checks Gradle wrappers
- Builds RealmShark → Tomato (sequential)
- Copies dependencies automatically
- Generates `build.log` with full details
- Shows build times for each project
- Pretty-printed status messages

**Usage**:
```bash
./build-all.sh
```

**Output**: 
- Detailed build log in `build.log`
- Both JAR files in respective `build/libs/` folders
- Dependency JARs in both `libs/` folders

### `build-quick.sh` (Fast)
**Purpose**: Quick build with parallel compilation (where possible)
**Features**:
- Builds projects sequentially but efficiently
- Minimal logging
- Shows only errors
- Good for rapid iteration

**Usage**:
```bash
./build-quick.sh
```

### `run.sh` (Launcher)
**Purpose**: Launch either or both applications
**Usage**:
```bash
./run.sh <option>

Options:
  realmshark (rs)  - Run RealmShark backend
  tomato (tom)     - Run Tomato with GUI
  both             - Run both in background
```

---

## 🔧 Manual Build

If you prefer to build manually:

### Build RealmShark Only
```bash
cd RealmShark
./gradlew clean shadowJar
```

### Build Tomato Only
```bash
cd Tomato
./gradlew clean shadowJar
```

### Build Dependencies Manually
```bash
cd RealmShark
mkdir -p libs
cp build/libs/RealmShark-*.jar libs/

# Copy to Tomato
cd ../Tomato
mkdir -p libs
cp ../RealmShark/build/libs/RealmShark-*.jar libs/
```

---

## 🌿 Git Branches & Worktrees

Each folder is a Git worktree pointing to a specific branch:

- **RealmShark** → `realmshark` branch
- **Tomato** → `tomato_integration` branch (detached HEAD)
- **.git** → Shared repository at `<repo-root>/.git`

### View Worktrees
```bash
git worktree list
```

### Switch to RealmShark and inspect
```bash
cd RealmShark
git status
git log --oneline -5
```

### Switch to Tomato and inspect
```bash
cd Tomato
git status
git log --oneline -5
```

---

## 🎯 Project Details

### RealmShark Backend
**Branch**: `realmshark`  
**Purpose**: Packet sniffer that detects game loot drops  
**Output**: 
- `RealmShark-v1.2.2.jar` (3.2 MB)

**Key Code**:
- `src/main/java/realmshark/RealmShark.java` - Dual-mode launcher
- `src/main/java/realmshark/bridge/RealmSharkLoutBridge.java` - HTTP bridge
- `src/main/java/realmshark/ui/RealmSharkDesktopUI.java` - Standalone UI

**Features**:
- Packet sniffing
- Loot detection
- HTTP POST to Discord bot
- Real-time status display

### Tomato Frontend
**Branch**: `tomato_integration`  
**Purpose**: GUI application showing **11 tabs** with monitoring capabilities  
**Output**: 
- `PPE-Sniffer-v1.0.jar` (8.7 MB, includes embedded RealmShark JAR)

**Key Code**:
- `src/main/java/tomato/Tomato.java` - Main application entry
- `src/main/java/tomato/gui/TomatoGUI.java` - 11-tab tabbed interface (★ MODIFIED)

**Tabs**:
1. Chat
2. Key-pops
3. Security
4. Characters
5. Statistics
6. Daily Quests
7. My Info
8. DPS Logger
9. **Packet Logs** (NEW - RealmShark)
10. **Bot Sends** (NEW - RealmShark)
11. **Bridge Status** (NEW - RealmShark)

---

## 🔄 Build Workflow

### Dependency Flow
```
RealmShark (realmshark branch)
    │
    └─ ./gradlew clean shadowJar
       └─ Produces: RealmShark-v1.2.2.jar
          │
          └─ Copied to: 
             • RealmShark/libs/
             • Tomato/libs/
                │
                └─ Tomato (tomato_integration branch)
                   │
                   └─ ./gradlew clean shadowJar
                      └─ Produces: PPE-Sniffer-v1.0.jar
                         (With embedded RealmShark JAR)
```

### Build Order
1. **RealmShark** builds first
   - Creates `RealmShark-v1.2.2.jar`
   - This is the dependency

2. **Dependencies** copied
   - `RealmShark-v1.2.2.jar` → `RealmShark/libs/`
   - `RealmShark-v1.2.2.jar` → `Tomato/libs/`

3. **Tomato** builds next
   - Uses `libs/RealmShark-v1.2.2.jar` as dependency
   - Creates `PPE-Sniffer-v1.0.jar` (8.7 MB)
   - Final JAR includes all dependencies (embedded)

---

## ✅ Verification Checklist

After building, verify everything is working:

```bash
# Check JAR files exist
ls -lh RealmShark/build/libs/RealmShark-*.jar
ls -lh Tomato/build/libs/PPE-Sniffer-*.jar

# Check dependencies were copied
ls -lh RealmShark/libs/RealmShark-*.jar
ls -lh Tomato/libs/RealmShark-*.jar

# Test Tomato (should show GUI with 11 tabs)
./run.sh tomato

# Check build log
cat build.log
```

---

## 🐛 Troubleshooting

### Build fails with "RealmShark JAR not found"
**Solution**: Run `./build-all.sh` to build RealmShark first, which copies the JAR to libs/

### Tomato won't compile with "cannot find symbol"
**Solution**: Ensure `Tomato/libs/RealmShark-v1.2.2.jar` exists. Run `./build-all.sh`

### "gradlew: Permission denied"
**Solution**: `chmod +x RealmShark/gradlew Tomato/gradlew`

### Git worktree conflicts
**Solution**: 
```bash
git worktree list  # View all worktrees
git worktree prune # Clean up stale entries
```

### Build log not generated
**Solution**: Check permissions on rotmg folder: `ls -ld <repo-root>`

---

## 📊 Build Statistics

| Component | Size | Build Time | Language |
|-----------|------|-----------|----------|
| RealmShark | 3.2 MB | ~1s | Java 8 |
| Tomato | 8.7 MB | ~3s | Java 8 |
| **Total** | **11.9 MB** | **~4s** | Java 8 |

---

## 🔗 Integration with rotmgppebot

The Python Discord bot (`rotmgppebot/`) receives loot data from Tomato via HTTP:

1. Tomato detects loot drops
2. Sends HTTP POST to `http://localhost:8787/realmshark/ingest`
3. rotmgppebot receives and validates with link tokens
4. Bot logs items to Discord

**Configure in Tomato properties**:
```properties
realmshark.bridge.enabled=true
realmshark.bridge.guild_id=YOUR_GUILD_ID
realmshark.bridge.link_token=TOKEN_FROM_DISCORD
realmshark.bridge.endpoint=http://localhost:8787/realmshark/ingest
```

---

## 📚 Documentation Files

- **README.md** (this file) - Quick start and overview
- **INTEGRATION_SUMMARY.md** - Technical integration details
- **BUILD_STRUCTURE.md** - Build organization and folder layout
- **STATUS.md** - Current project status and verification checklist
- **build.log** - Generated after running `./build-all.sh`

---

## 🎓 Key Features

✅ **Separate Worktrees**
- RealmShark and Tomato in separate directories
- No branch switching needed
- Both can be modified simultaneously

✅ **Unified Build Process**
- Single command builds both projects
- Automatic dependency management
- Dependency JAR shared between projects

✅ **No Branch Switching**
- Forget about `git checkout realmshark/tomato`
- Each folder = its own branch

✅ **Error-Free Builds**
- Both projects compile without warnings
- All dependencies resolved automatically
- Build logs for debugging

✅ **Complete Documentation**
- 4 markdown files explaining the setup
- Quick start guide (this file)
- Troubleshooting section

---

## 🚀 Next Steps

1. **Build the project**: `./build-all.sh`
2. **Test Tomato**: `./run.sh tomato`
3. **Test RealmShark**: `./run.sh realmshark`
4. **Configure bridge** properties at runtime
5. **Monitor loot drops** in the 3 new RealmShark tabs
6. **Integrate with rotmgppebot** for Discord logging

---

**Project Status**: 🟢 **READY FOR PRODUCTION**

*Last Updated: 2026-03-22*
