import json
import os
import re
import shutil
from typing import Callable
from pathlib import Path

import requests


def modify_file(file_path: str, func: Callable[[str], str]):
    with open(file_path, "r") as f:
        content = f.read()

    content = func(content)

    with open(file_path, "w") as f:
        f.write(content)


def modify_readme(last_major: int, last_minor: int, last_patch: int, major: int, minor: int, patch: int) -> str:
    file_path = 'README.md'
    with open(file_path, "r") as f:
        lines = f.readlines()

    out = []
    prev_line = None
    for i, line in enumerate(lines):
        current_version = f"{major}.{minor}.{patch}"
        if prev_line is not None and prev_line.startswith('|-----------------|----------------|----------------|----------------|'):
            if last_major != major or last_minor != minor:
                out.append(f"| {current_version} | ✔️              | ✔️              | ✔️              |\n")
            else:
                last_version = f"{last_major}.{last_minor}.{last_patch}"
                if f"- {last_version}" in line:
                    line = line.replace(last_version, current_version)
                elif last_version in line:
                    line = line.replace(last_version, f"{last_version} - {current_version}")
        line = re.sub(r'mc:\s*.*', f'mc: {major}.{minor}.{patch}', line)
        out.append(line)
        prev_line = line

    with open(file_path, "w", encoding="utf-8") as f:
        f.writelines(out)


def modify_forge_mod(content: str, major: int, minor: int) -> str:
    content = re.sub(r'versionRange = "\[.*,\)"', f'versionRange = "[{major}.{minor}.0,)"', content)
    return content


def modify_fabric_mod_json(content: str, major: int, minor: int) -> str:
    content = re.sub(r'"minecraft": "~.*",', f'"minecraft": "~{major}.{minor}.0",', content)
    return content


def modify_gradle_properties(content: str, latest: str, lex: str) -> str:
    content = re.sub(r'minecraft_version\s*=\s*.*', f'minecraft_version = {latest}', content)
    content = re.sub(r'lexforge_version\s*=\s*.*', f'lexforge_version = {lex}', content)
    return content


def update_ci_data(curr_dir: str, latest: str, lex: str):
    with open('ci-data.json', "r") as f:
        ci_data = json.load(f)

    build_data = ci_data['build_data']
    run_data = ci_data['run_data']

    build_data.insert(1, {
      "dir": curr_dir,
      "mc": latest,
      "lex": lex,
      "neo": "0-beta",
      "java": "21"
    })

    for ml_type, modloader in { "fabric": "fabric", "neoforge": "neoforge", "lexforge": "forge" }.items():
        run_data.insert(0, {
            "mc": latest,
            "type": ml_type,
            "modloader": modloader,
            "regex": f".*{modloader}.*",
            "java": "21"
        })

    with open('ci-data.json', "w") as f:
        f.write(json.dumps(ci_data, indent='\t'))


def update_current_version_json(file_path: Path, current_version: dict, curr_dir: str, major: int, minor: int, patch: int):
    current_version['major'] = major
    current_version['minor'] = minor
    current_version['patch'] = patch
    current_version['dir'] = curr_dir
    with open(file_path, 'w') as f:
        f.write(json.dumps(current_version, indent='\t'))


def prepare_new_dir(curr_dir: str, latest: str, major: int, minor: int, patch: int, lex: str) -> str:
    new_dir = f"{major}_{minor}"
    shutil.copytree(curr_dir, new_dir, dirs_exist_ok=True)

    modify_file(os.path.join(new_dir, 'gradle.properties'),
                lambda c: modify_gradle_properties(c, latest, lex))
    modify_file(os.path.join(new_dir, 'src', 'fabric', 'resources', 'fabric.mod.json'),
                lambda c: modify_fabric_mod_json(c, major, minor))
    modify_file(os.path.join(new_dir, 'src', 'lexforge', 'resources', 'META-INF', 'mods.toml'),
                lambda c: modify_forge_mod(c, major, minor))
    modify_file(os.path.join(new_dir, 'src', 'neoforge', 'resources', 'META-INF', 'neoforge.mods.toml'),
                lambda c: modify_forge_mod(c, major, minor))

    return new_dir


def get_lexforge_version(mc_version: str) -> str:
    url = 'https://meta.prismlauncher.org/v1/net.minecraftforge/index.json'
    response = requests.get(url)
    data = response.json()
    '''
    "versions": [
        {
            "recommended": false,
            "releaseTime": "2025-11-10T19:31:26+00:00",
            "requires": [
                {
                    "equals": "1.21.10",
                    "uid": "net.minecraft"
                }
            ],
            "sha256": "1562b8e42aae92b8b8f502b392841f3107e407257d14e419842660d8d51db26e",
            "version": "60.0.18"
        },
    '''
    for version in data['versions']:
        for require in version['requires']:
            if require['equals'] == mc_version and require['uid'] == 'net.minecraft':
                return version['version']

    raise Exception(f"Failed to find Lexforge version for {mc_version}")


def check_latest_mc_version():
    current_version_file_path = Path(__file__).parent / 'current-version.json'
    with open(current_version_file_path) as f:
        current_version = json.load(f)

    current_major = current_version['major']
    current_minor = current_version['minor']
    current_patch = current_version['patch']
    curr_dir = current_version['dir']

    url = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    response = requests.get(url)
    data = response.json()

    latest_release = data['latest']['release']  # (1.21.5)
    print("Latest Release", latest_release)
    major_minor_patch = latest_release.split('.')  # (1, 21, 5) or (1, 21)
    if len(major_minor_patch) < 2:
        raise Exception(major_minor_patch, "Length < 2")
    elif len(major_minor_patch) > 3:
        raise Exception(major_minor_patch, "Length > 3")

    major = int(major_minor_patch[0])
    minor = int(major_minor_patch[1])
    patch = 0 if len(major_minor_patch) < 3 else int(major_minor_patch[2])
    if current_major != major or current_minor != minor or current_patch != patch:
        lex = get_lexforge_version(latest_release)
        print("New Release found!")
        env_file = os.getenv('GITHUB_ENV')
        if env_file:
            if current_major != major or current_minor != minor:
                curr_dir = prepare_new_dir(curr_dir, latest_release, major, minor, patch, lex)
            update_current_version_json(current_version_file_path, current_version.copy(), curr_dir, major, minor, patch)
            update_ci_data(curr_dir, latest_release, lex)
            modify_readme(current_major, current_minor, current_patch, major, minor, patch)
            with open(env_file, 'a') as f:
                f.write(f"LATEST_VERSION={latest_release}\n")
                f.write(f"LATEST_VERSION_DIR={curr_dir}\n")
        else:
            raise FileNotFoundError("Failed to find GITHUB_ENV file!")


if __name__ == '__main__':
    check_latest_mc_version()
