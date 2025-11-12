import os
import re
import shutil
from typing import Callable

import requests


def modify_file(file_path: str, func: Callable[[str], str]):
    with open(file_path, "r") as f:
        content = f.read()

    content = func(content)

    with open(file_path, "w") as f:
        f.write(content)


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


def modify_lifecycle(curr_dir: str, latest: str, lex: str):
    lifecycle_yml = '.github/workflows/lifecycle.yml'
    with open(lifecycle_yml, "r", encoding="utf-8") as f:
        lines = f.readlines()

    out = []
    for i, line in enumerate(lines):
        out.append(line)
        if line.strip() == "# new-mc-version build data":
            out.append(f"            {{\"dir\": \"{curr_dir}\", \"mc\": \"{latest}\", \"lex\": \"{lex}\", \"neo\": \"1-beta\", \"java\": \"21\"}},\n")
        elif line.strip() == "# new-mc-version run data":
            out.append(f"            {{\"mc\": \"{latest}\", \"type\": \"lexforge\", \"modloader\": \"forge\", \"regex\": \".*forge.*\", \"java\": \"21\"}},\n")
            out.append(f"            {{\"mc\": \"{latest}\", \"type\": \"neoforge\", \"modloader\": \"neoforge\", \"regex\": \".*neoforge.*\", \"java\": \"21\"}},\n")
            out.append(f"            {{\"mc\": \"{latest}\", \"type\": \"fabric\", \"modloader\": \"fabric\", \"regex\": \".*fabric.*\", \"java\": \"21\"}},\n")

    with open(lifecycle_yml, "w", encoding="utf-8") as f:
        f.writelines(out)


def modify_script_file(content: str, curr_dir: str, major: int, minor: int, patch: int) -> str:
    content = re.sub(r'current_major\s*=\s*\d+', f'current_major = {major}', content)
    content = re.sub(r'current_minor\s*=\s*\d+', f'current_minor = {minor}', content)
    content = re.sub(r'current_patch\s*=\s*\d+', f'current_patch = {patch}', content)
    content = re.sub(r'curr_dir\s*=\s*[\'"][^\'"]*[\'"]', f'curr_dir = \'{curr_dir}\'', content)
    return content


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
    url = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    current_major = 1
    current_minor = 21
    current_patch = 10
    curr_dir = '1_21_10'

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
            with open(env_file, 'a') as f:
                f.write(f"LATEST_VERSION={latest_release}\n")
            if current_major != major or current_minor != minor:
                curr_dir = prepare_new_dir(curr_dir, latest_release, major, minor, patch, lex)

            modify_file(__file__, lambda c: modify_script_file(c, curr_dir, major, minor, patch))
            modify_lifecycle(curr_dir, latest_release, lex)
        else:
            raise FileNotFoundError("Failed to find GITHUB_ENV file!")


if __name__ == '__main__':
    check_latest_mc_version()
