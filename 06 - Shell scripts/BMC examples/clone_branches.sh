#!/bin/bash

# development/<VER>
# realease/<VER>
# Server_INT_<VER>
# EM_Server_CI_<VER>

echo "*** Clean temp ${PROD} repo"
echo
lrepo="/p/gap/tmp/repo/${PROD}"
export lrepo
rm -rf "${lrepo}"
mkdir "${lrepo}"
cd "${lrepo}"

res=$?
export res
if [ "${res}" -ne 0 ]; then
        echo "*** Failed cleaning temp repo $res"
        exit $res
fi

echo "*** Cloning $SOURCE_BRANCH on $REPO"
echo
git clone -b $SOURCE_BRANCH --single-branch $REPO "${lrepo}"
git checkout $SOURCE_BRANCH
git pull
echo "*** Creating $TARGET_BRANCH"
echo
git branch $TARGET_BRANCH
git checkout $TARGET_BRANCH
git branch -a

if [ "$HAS_JENKINS" = "YES" ]; then
        echo "*** Updating Yaml files on $TARGET_BRANCH"
        echo
        find . -type f -name "*params.yaml" -exec sed -i "s/$SOURCE_VER/$TARGET_VER/g" {} +
        # sed -i -- "s/$SOURCE_VER/$TARGET_VER/g" *params.yaml
        res=$?
        export res
        if [ "${res}" -ne 0 ]; then
                echo "*** Failed creating new branches $res"
                exit $res
        fi

        git add *.yaml
        git commit -m "creating a new branch for $TARGET_VER builds"
fi

echo "*** Pushing $TARGET_BRANCH to Bitbucket"
echo

git push --set-upstream origin $TARGET_BRANCH
res=$?
export res
if [ "${res}" -ne 0 ]; then
        echo "***Failed pushing $TARGET_BRANCH $res"
        exit $res
fi


if [ "${HAS_CI}" = "YES" ]; then

        if [ "${MULTI_CI}" = "YES" ]; then

                echo "*** Creating Multi CI branches"
                echo
                git branch $CI_BRANCH_PREFIX/server
                git checkout $CI_BRANCH_PREFIX/server
                git branch -a
                git push --set-upstream origin $CI_BRANCH_PREFIX/server
                res=$?
                export res
                if [ "${res}" -ne 0 ]; then
                        echo "*** Failed pushing $CI_BRANCH_PREFIX/server $res"
                        exit $res
                fi

                git branch $CI_BRANCH_PREFIX/client
                git checkout $CI_BRANCH_PREFIX/client
                git branch -a
                git push --set-upstream origin $CI_BRANCH_PREFIX/client
                res=$?
                export res
                if [ "${res}" -ne 0 ]; then
                        echo "*** Failed pushing $CI_BRANCH_PREFIX/client $res"
                        exit $res
                fi

                git branch $CI_BRANCH_PREFIX/web
                git checkout $CI_BRANCH_PREFIX/web
                git branch -a
                git push --set-upstream origin $CI_BRANCH_PREFIX/web
                res=$?
                export res
                if [ "${res}" -ne 0 ]; then
                        echo "***Failed pushing $CI_BRANCH_PREFIX/web $res"
                        exit $res
                fi

        else
                echo "*** Creating a single CI branch"
                echo
                git branch $CI_BRANCH_PREFIX
                git checkout $CI_BRANCH_PREFIX
                git branch -a
                git push --set-upstream origin $CI_BRANCH_PREFIX
                res=$?
                export res
                if [ "${res}" -ne 0 ]; then
                        echo "***Failed pushing $CI_BRANCH_PREFIX $res"
                        exit $res
                fi
        fi
fi

echo
echo "*** ${PROD} new branches were created successfully"
echo
exit 0
