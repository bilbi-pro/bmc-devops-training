#!/bin/bash

WORKING_DIR=$1
VERSION=$2
txt_postfix=_All.txt
sha256_postfix=sha256_All.txt
sha256_PP_postfix=sha256_PP_All.txt
cd ${WORKING_DIR}
echo $(rm -f *.txt)

items=$(ls * | cut -d _ -f 1 | cut -d . -f 1 | sort -u)
if [ $(echo $items | wc -w) -gt 1 ]
	then
		echo "items is more than one - handling OneInstall_HOP"
		for i in $items ;
			do
			# create sha256_All.txt
			checksum_txt_file=DROST.$VERSION.$sha256_postfix
			touch $checksum_txt_file
			# create sha256_PP_All.txt file
			checksum_PP_txt_file=DROST.$VERSION.$sha256_PP_postfix
			touch $checksum_PP_txt_file
			DR=$(echo ${i} | grep -E -o '^([A-Z]{3}(2|[A-Z])[A-Z])')
			files=$(ls -I "*${txt_postfix}" | grep ${DR})
			for f in $files ;
				do
				if [[ $f == *"PP"* ]];
					then
						sha256sum ${f} | tee -a $checksum_PP_txt_file ${f}-sha256.txt ;
					else
						sha256sum ${f} | tee -a $checksum_txt_file ${f}-sha256.txt ;
				fi
			done
		done
	else
		echo "items is exactly one - handling all HOP's except OneInstall_HOP"
		for i in $items ;
			do
			checksum_txt_file=$i.$VERSION.$sha256_postfix
			touch $checksum_txt_file
			DR=$(echo ${i} | grep -E -o '^([A-Z]{3}(2|[A-Z])(2|[A-Z])|bmcifo-[0-9]{4})')
			files=$(ls -I "*${sha256_postfix}" | grep ${DR})
			for f in $files ;
				do sha256sum ${f} | tee -a $checksum_txt_file ${f}-sha256.txt ;
		done
	done
fi
