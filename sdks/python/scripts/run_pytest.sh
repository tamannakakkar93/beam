#!/bin/bash
#
#    Licensed to the Apache Software Foundation (ASF) under one or more
#    contributor license agreements.  See the NOTICE file distributed with
#    this work for additional information regarding copyright ownership.
#    The ASF licenses this file to You under the Apache License, Version 2.0
#    (the "License"); you may not use this file except in compliance with
#    the License.  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
# Utility script for tox.ini for running unit tests.
#
# Runs tests in parallel, except those not compatible with xdist. Combines
# exit statuses of runs, special-casing 5, which says that no tests were
# selected.
#
# $1 - suite base name
# $2 - additional arguments not parsed by tox (typically module names or
#   '-k keyword')
# $3 - optional arguments to pytest

envname=${1?First argument required: suite base name}
posargs=$2
pytest_args=$3

if [[ $pytest_args =~ "-m" ]] || [[ $posargs =~ "-m" ]]; then
  echo "$0 cannot be called with -m as it interferes with 'no_xdist' logic, see BEAM-12985."
  exit 1
fi

# strip leading/trailing quotes from posargs because it can get double quoted as its passed through.
posargs=$(sed -e 's/^"//' -e 's/"$//' -e "s/'$//" -e "s/^'//" <<<$posargs)
echo "pytest_args: $pytest_args"
echo "posargs: $posargs"

# Run with pytest-xdist and without.
pytest -v -rs -o junit_suite_name=${envname} \
  --junitxml=pytest_${envname}.xml -m 'not no_xdist' -n 6 --import-mode=importlib ${pytest_args} --pyargs ${posargs}
status1=$?
pytest -v -rs -o junit_suite_name=${envname}_no_xdist \
  --junitxml=pytest_${envname}_no_xdist.xml -m 'no_xdist' --import-mode=importlib ${pytest_args} --pyargs ${posargs}
status2=$?

# Exit with error if no tests were run in either suite (status code 5).
if [[ $status1 == 5 && $status2 == 5 ]]; then
  exit $status1
fi

# Exit with error if one of the statuses has an error that's not 5.
if [[ $status1 != 0 && $status1 != 5 ]]; then
  exit $status1
fi
if [[ $status2 != 0 && $status2 != 5 ]]; then
  exit $status2
fi
