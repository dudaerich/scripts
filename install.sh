#!/bin/bash

function groovy-installer {
    basename=$(basename "$1")
    execFile=bin/${basename%.*}
    echo "#!/bin/bash" > $execFile
    echo "groovy $PWD/$script \"\$@\"" >> $execFile
    chmod a+x $execFile
}

if [ ! -e bin ]; then
    mkdir bin
fi

for script in src/*
do
    extension="${script##*.}"
    ${extension}-installer $script
done

rcLine="PATH=$PWD/bin:\$PATH"

if ! grep "$rcLine" ~/.bashrc > /dev/null; then
    echo "$rcLine" >> ~/.bashrc
    echo "To finish the installation login again or type the command to your bash"
    echo "source ~/.bash_profile"
fi