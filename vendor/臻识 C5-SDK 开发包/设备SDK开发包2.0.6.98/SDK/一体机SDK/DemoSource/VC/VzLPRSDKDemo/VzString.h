#pragma once

#include <string>
#include <vector>
using namespace std;

class CVzString
{
public:
	CVzString(void);
	~CVzString(void);

	static void split(std::string& s, std::string& delim,std::vector< std::string >* ret);
	static char* EncodeStr( const char *str );
};
