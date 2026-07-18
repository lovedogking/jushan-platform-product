#include "StdAfx.h"
#include "Base64.h"
#include "VzString.h"

CVzString::CVzString(void)
{
}

CVzString::~CVzString(void)
{
}

void CVzString::split(std::string& s, std::string& delim,std::vector< std::string >* ret)  
{  
	size_t last = 0;  
	size_t index=s.find_first_of(delim,last);  

	while (index!=std::string::npos)  
	{  
		ret->push_back(s.substr(last,index-last));  
		last=index+1;  
		index=s.find_first_of(delim,last);  
	}  

	if (index-last>0)  
	{  
		ret->push_back(s.substr(last,index-last));  
	}  
}

char* CVzString::EncodeStr( const char *str )
{
	char *pEncodeStr = NULL;

	char *pUTFName = NULL;
	pUTFName = gb2312_to_utf8_ex((char *)str);

	if( pUTFName != NULL )
	{
		pEncodeStr = (char*)base64Encode(pUTFName, strlen(pUTFName));

		free(pUTFName);
		pUTFName = NULL;
	}

	return pEncodeStr;
}