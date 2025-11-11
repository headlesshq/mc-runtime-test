import requests
import os
import re


def modify_lifecycle(curr_dir: str, latest: str, lex: int):
    with open('.github/workflows/lifecycle.yml', "r", encoding="utf-8") as f:
        lines = f.readlines()

    out = []
    for i, line in enumerate(lines):
        out.append(line)
        if line.strip() == "# new-mc-version build data":
            out.append(f"{{\"dir\": \"{curr_dir}\", \"mc\": \"{latest}\", \"lex\": \"{lex}.0.0\", \"neo\": \"1-beta\", \"java\": \"21\"}},\n")
        elif line.strip() == "# new-mc-version run data":
            out.append(f"{{\"mc\": \"{latest}\", \"type\": \"lexforge\", \"modloader\": \"forge\", \"regex\": \".*forge.*\", \"java\": \"21\"}},\n")
            out.append(f"{{\"mc\": \"{latest}\", \"type\": \"neoforge\", \"modloader\": \"neoforge\", \"regex\": \".*neoforge.*\", \"java\": \"21\"}},\n")
            out.append(f"{{\"mc\": \"{latest}\", \"type\": \"fabric\", \"modloader\": \"fabric\", \"regex\": \".*fabric.*\", \"java\": \"21\"}},\n")


    with open('.github/workflows/lifecycle.yml', "w", encoding="utf-8") as f:
        f.writelines(out)


def modify_script_file(major: int, minor: int, patch: int, lex: int):
    script_path = __file__
    with open(script_path, "r") as f:
        content = f.read()

    content = re.sub(r'current_major\s*=\s*\d+', f'current_major = {major}', content)
    content = re.sub(r'current_minor\s*=\s*\d+', f'current_minor = {minor}', content)
    content = re.sub(r'current_patch\s*=\s*\d+', f'current_patch = {patch}', content)
    content = re.sub(r'current_lex\s*=\s*\d+', f'current_lex = {lex}', content)

    with open(script_path, "w") as f:
        f.write(content)


def check_latest_mc_version():
    url = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    current_major = 1
    current_minor = 21
    current_patch = 5

    current_lex = 55
    curr_dir = '1_21_5'

    response = requests.get(url)
    data = response.json()

    latest_release = data['latest']['release'] # (1.21.5)
    print("Latest Release", latest_release)
    major_minor_patch = latest_release.split('.')  # (1, 21, 5) or (1, 21)
    if len(major_minor_patch) < 2:
        raise Exception(major_minor_patch, "Length < 2")
    elif len(major_minor_patch) > 3:
        raise Exception(major_minor_patch, "Length > 3")

    latest_major = int(major_minor_patch[0])
    latest_minor = int(major_minor_patch[1])
    latest_patch = 0 if len(major_minor_patch) < 3 else int(major_minor_patch[2])
    if current_major != latest_major or current_minor != latest_minor or current_patch != latest_patch:
        print("New Release found!")
        env_file = os.getenv('GITHUB_ENV')
        if env_file:
            with open(env_file, 'a') as f:
                f.write(f"LATEST_VERSION={latest_release}\n")
            modify_script_file(latest_major, latest_minor, latest_patch, current_lex + 1)
            modify_lifecycle(curr_dir, latest_release, current_lex + 1)
        else:
            raise FileNotFoundError("Failed to find GITHUB_ENV file!")


if __name__ == '__main__':
    check_latest_mc_version()
