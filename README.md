# WorldDownloader

A Minecraft Forge mod for version 1.20.1 that allows you to download server world chunks to your local machine. Supports both vanilla and modded servers.

## Features

- **Chunk Download**: Automatically captures chunks as you explore the server world
- **Block Entity Support**: Saves chests, signs, furnaces, and all other block entities
- **Entity Support**: Captures entities in the world (excluding players)
- **Modded Content Support**: Full support for modded blocks, items, and entities
- **Region File Format**: Saves in standard Minecraft Anvil format (.mca files)
- **Easy Commands**: Simple commands to control downloading

## Requirements

- Minecraft 1.20.1
- Forge 47.2.0 or later

## Installation

1. Install Minecraft Forge for 1.20.1
2. Download the WorldDownloader mod jar
3. Place the jar file in your `.minecraft/mods` folder
4. Launch Minecraft with Forge

## Usage

### Commands

- `/wdl start [name]` - Start downloading the world (optionally specify a world name)
- `/wdl stop` - Stop downloading and save the world
- `/wdl status` - Show current download status
- `/wdl capture` - Manually capture all currently loaded chunks
- `/wdl help` - Show help information

### How to Download a World

1. Connect to a server
2. Run `/wdl start myworld` to begin downloading
3. Explore the areas you want to download
4. Run `/wdl stop` when finished
5. Find your downloaded world in the singleplayer menu

## Configuration

The mod can be configured via the `worlddownloader-client.toml` file in the `config` folder:

- `captureEntities` - Whether to capture entities (default: true)
- `captureBlockEntities` - Whether to capture block entities (default: true)
- `autoSaveInterval` - How often to auto-save chunks in seconds (default: 60)
- `chunksBeforeSave` - Number of chunks before auto-saving (default: 32)
- `captureModded` - Whether to capture modded content (default: true)

## Building from Source

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## License

MIT License

## Credits

World Downloader Team