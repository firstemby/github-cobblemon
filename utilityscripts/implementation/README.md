Hello!  
This is a script for checking asset implementation.  

You will need Python. If you don't have it already, install it from here: https://www.python.org/downloads/

Then you will need external libraries, open the terminal (search for `cmd` on Windows) and type in the following: `pip install pandas soundfile`  
If the terminal says "Requirement already satisfied," but the script doesn't work, try this instead:
- Type in `py -0p`, this will show installed versions and highlight your default with `*`
- Type in `py -3.14 -m pip install pandas soundfile`, replace `-3.14` with your default version

The script assumes `cobblemon-assets` is in the same directory as `cobblemon`, the main mod where you're running this script from.  
If not, create "assets_directory.txt" and paste in the path to where your assets repository is cloned.  
For example `D:\Dev\Cobblemon\Repository\cobblemon-assets`  
This text file will be ignored by Git.

If there are any issues, or you need help with anything, poke Sterr on Discord.