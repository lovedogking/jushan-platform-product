#pragma once

#include <string>
using namespace std;

class CVzFile
{
public:
	CVzFile(void);
	~CVzFile(void);

	static string GetAppPath();
	static bool WriteFile(const char *filepath, const char *buffer);
	static bool WriteTxtFile(const char *filepath, LPCSTR strText);
	static bool WriteTxtFileAppend(const char *filepath, LPCSTR strText);
	static bool ReadTextFile( const char *filepath, char *buffer, int max_len );

	static string GetConfigValue(string sFilePath, string sAppName, string sKeyName);

	static int IsPathExist(const char *filename);

	static long GetFileSize(const char *filename);

	static string BrowsPath(char *pInitPath, LPCTSTR lpstrFilter, HWND hwnd);

	static int GetLineCount(const char *filepath);
};
