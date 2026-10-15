"""Compile actual core sources and run JUnit independently of Android configuration.

Requires JAVA_HOME and SCROLLFIT_KOTLIN_COMPILER_DIR containing Kotlin 2.4.10
compiler/stdlib/runtime dependencies and JUnit 4.13.2/Hamcrest jars. Downloads
nothing. Prefer ./gradlew -p verification/core :core:test when Gradle is available.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

repo = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')
compiler_dir = os.environ.get('SCROLLFIT_KOTLIN_COMPILER_DIR')
if not java_home or not compiler_dir:
    sys.exit('Set JAVA_HOME and SCROLLFIT_KOTLIN_COMPILER_DIR; see the module docstring.')
java = Path(java_home) / 'bin/java'
jars = sorted(Path(compiler_dir).glob('*.jar'))
if not java.is_file() or not jars:
    sys.exit('Java or compiler/dependency jars are missing.')
classpath = os.pathsep.join(str(p) for p in jars)
sources = sorted((repo / 'core/src/main').rglob('*.kt')) + sorted((repo / 'core/src/test').rglob('*.kt'))
with tempfile.TemporaryDirectory(prefix='scrollfit-core-') as temporary:
    output = Path(temporary) / 'classes'
    output.mkdir()
    print(f'Compiling {len(sources)} actual Kotlin source/test files; JVM target 17.', flush=True)
    result = subprocess.run([str(java), '-XX:ActiveProcessorCount=2', '-Xmx768m', '-cp', classpath,
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-classpath', classpath, '-d', str(output), *map(str, sources)])
    if result.returncode:
        sys.exit(result.returncode)
    tests = []
    for path in sources:
        if path.name.endswith('Test.kt'):
            package = next(line.split()[1] for line in path.read_text().splitlines() if line.startswith('package '))
            tests.append(package + '.' + path.stem)
    print(f'Running {len(tests)} JUnit test classes.', flush=True)
    sys.exit(subprocess.run([str(java), '-XX:ActiveProcessorCount=2', '-cp', str(output) + os.pathsep + classpath,
        'org.junit.runner.JUnitCore', *tests]).returncode)
