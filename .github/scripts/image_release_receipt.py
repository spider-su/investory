"""Write a bounded provenance receipt after successful scan and SBOM upload."""
import json
import os
from pathlib import Path
import re


def receipt(environment):
    value = {key: environment[name] for key, name in {
        'sha': 'RELEASE_SHA', 'digest': 'RELEASE_DIGEST', 'image': 'RELEASE_IMAGE',
        'run_id': 'GITHUB_RUN_ID', 'run_attempt': 'GITHUB_RUN_ATTEMPT',
        'tests_run_id': 'RELEASE_TESTS_RUN_ID', 'tests_run_attempt': 'RELEASE_TESTS_RUN_ATTEMPT',
    }.items()}
    if not re.fullmatch(r'[0-9a-f]{40}', value['sha']):
        raise ValueError('Full release commit SHA required')
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', value['digest']):
        raise ValueError('Published image digest required')
    if value['image'] != 'aserobaba/investory':
        raise ValueError('Unexpected release image repository')
    if not all(value[key].isdigit() for key in ('run_id', 'run_attempt')):
        raise ValueError('Actions run and attempt identity required')
    if value['tests_run_id'] and not (value['tests_run_id'].isdigit() and value['tests_run_attempt'].isdigit()):
        raise ValueError('Invalid upstream Tests identity')
    return value


if __name__ == '__main__':
    path = Path('image-release.json')
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(receipt(os.environ), indent=2) + '\n')
    temporary.replace(path)
