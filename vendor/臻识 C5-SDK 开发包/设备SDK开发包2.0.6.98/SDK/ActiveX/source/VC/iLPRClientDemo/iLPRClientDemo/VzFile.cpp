#include "StdAfx.h"
#include "VzFile.h"
#include <io.h>

#include <Psapi.h>
#pragma comment(lib,"Psapi.lib")

CVzFile::CVzFile(void)
{
}

CVzFile::~CVzFile(void)
{
}

// 获取当前程序路径
string CVzFile::GetAppPath()
{
	string sAppPath;
    
    TCHAR tszModule[MAX_PATH + 1] = { 0 };
    ::GetModuleFileName(NULL, tszModule, MAX_PATH);
    
	sAppPath.assign(tszModule);
	int pos = sAppPath.find_last_of(_T('\\'));
	sAppPath = sAppPath.substr(0, pos);
	
	return sAppPath;
}

bool CVzFile::WriteFile(const char *filepath, const char *buffer)
{
	int num = 0;

	FILE *fp = NULL;
	fp = fopen(filepath, "w+");

	if( fp == NULL )
	{
		return false;
	}

	num = fwrite( buffer, sizeof(char), strlen(buffer), fp );
	fclose( fp );

	return num > 0;
}


bool CVzFile::WriteTxtFile(const char *filepath, LPCSTR strText)
{
	int num = 0;

	FILE *fp = NULL;
	fp = fopen(filepath, "w+");

	if( fp == NULL )
	{
		return false;
	}

	num = fwrite( strText, sizeof(char), strlen(strText), fp );
	fclose( fp );

	return num > 0;
}

bool CVzFile::WriteTxtFileAppend(const char *filepath, LPCSTR strText)
{
	int num = 0;

	FILE *fp = NULL;
	fp = fopen(filepath, "a+");

	if( fp == NULL )
	{
		return false;
	}

	num = fwrite( strText, sizeof(char), strlen(strText), fp );
	fclose( fp );

	return num > 0;
}

// 读取文本文件
bool CVzFile::ReadTextFile( const char *filepath, char *buffer, int max_len )
{
	int num = 0;

	FILE *fp = NULL;
	fp = fopen(filepath, "r");

	if( fp == NULL )
	{
		return false;
	}

	num = fread( buffer, sizeof(char), max_len, fp );
	fclose( fp );

	return num > 0;
}

string CVzFile::GetConfigValue(string sFilePath, string sAppName, string sKeyName)
{
	const int value_len = 256;
	char szValue[value_len] = {0};

	GetPrivateProfileString(sAppName.c_str(), sKeyName.c_str(), _T(""), szValue, value_len, sFilePath.c_str());

	string sValue(szValue);
	return sValue;
}

int CVzFile::IsPathExist(const char *filename)   
{   
	return (access(filename, NULL) == 0);   
}

// 获取文件的大小
long CVzFile::GetFileSize(const char *filename)
{
	FILE   *fp; 
    long   lRet = 0; 

	fp = fopen( filename, "r" );

    if ( fp == NULL )
	{
        return 0;
	}

    fseek( fp, 0, SEEK_END );
    lRet = ftell(fp);
    fclose(fp); 
    
	return lRet; 
}

string CVzFile::BrowsPath(char *pInitPath, LPCTSTR lpstrFilter, HWND hwnd)
{
	string sPath;

	// 获取开始当前文件所在的目录
	string sInitDir;
	string sInitPath(pInitPath);

	int nPos = sInitPath.find_last_of("\\");
	if ( nPos > 0 )
	{
		sInitDir = sInitPath.substr(0, nPos+1);
	}


	// 打开文件打开对话框，如果选中文件，则
	OPENFILENAME ofn;      // 公共对话框结构。
	char szFile[MAX_PATH] = {0}; // 保存获取文件名称的缓冲区。			

	// 初始化选择文件对话框。
	ZeroMemory(&ofn, sizeof(ofn));
	ofn.lStructSize = sizeof(ofn);
	ofn.hwndOwner = hwnd;
	ofn.lpstrFile = szFile;
	ofn.nMaxFile = sizeof(szFile);
	ofn.lpstrFilter = lpstrFilter;
	ofn.nFilterIndex = 1;
	ofn.lpstrFileTitle = NULL;
	ofn.nMaxFileTitle = 0;
	ofn.lpstrInitialDir = sInitDir.c_str();
	ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST;

	// 显示打开选择文件对话框。
	if ( GetOpenFileName(&ofn) )
	{
		sPath.assign(szFile);
	}

	return sPath;
}

int CVzFile::GetLineCount(const char *filepath)
{
	int count = 0;

	FILE *fp = NULL;
	fp = fopen(filepath, "r");

	if (fp == NULL)
	{
		return 0;
	}

	const int max_len = 1024;
	char buffer[max_len] = { 0 };

	while (fgets(buffer, max_len, fp) != NULL)
	{
		count++;
	}

	fclose(fp);
	return count;
}