#CPP :=arm-hisiv600-linux-g++
#SP  :=arm-hisiv600-linux-strip

CPP :=g++
SP  :=strip

EXEC = userapp

INCLUDES = -I./include
					
CFLAGS = $(INCLUDES) -D_LINUX -Wall -O3 -lrt -lpthread -Wl,-rpath=.

LIBS = -L./ -lVzLPRSDK
#LIBS = -L./lib -lVzLPRSDK \
		#./lib/libiconv.so.2 

OBJ_DIR = ./obj/
VPATH = $(OBJ_DIR)
FILES_CPP = $(wildcard *.cpp )
OBJS_CPP0 = $(notdir $(subst .cpp,.o, $(FILES_CPP)))
OBJS_CPP = $(addprefix $(OBJ_DIR),$(OBJS_CPP0))

all : $(EXEC)  install
$(EXEC): $(OBJS_CPP0)
	$(CPP) $(CFLAGS) -o $(EXEC) $(OBJS_CPP) $(LIBS)

%.o : %.cpp | depend
	$(CPP) -c $(CFLAGS) -o $(OBJ_DIR)$@ $< 

install:
	$(SP) $(EXEC)	
	
clean:
	rm -rf $(OBJ_DIR)
	rm -f $(EXEC)

.PHONY: depend
depend:
	$(shell if [ ! -d $(OBJ_DIR) ]; then mkdir $(OBJ_DIR); fi;)
