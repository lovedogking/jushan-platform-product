#pragma once

#include<VzLPRClientSDK.h>

#include <string>


class DeviceLPR
{
public:
	DeviceLPR(const char *pStrIP, VzLPRClientHandle hLPRClient)
		: hLPRClient(hLPRClient), nIdxWin(-1)
	{
		strcpy_s(strIP, 256, pStrIP);
		memset(nSerialHandle, 0, sizeof(int)*2);

		m_bShowRealResult	= FALSE;
		m_bSaveImg			= TRUE;
		m_bLoadOfflinePlate = FALSE;
		m_bSetOffline		= FALSE;
		m_bShowPlate		= FALSE;
		m_nImgSize			= 0;
		m_nCurIO			= 0;
		m_nCurSerialPort	= 0;
		m_nBoardType        = 0;
		m_nCloudLogin       = 0;
		m_nTalkHandle       = 0; 

	}

	void SetWinIdx(int _nIdxWin)
	{
		nIdxWin = _nIdxWin;
	}

	int GetWinIdx() const
	{
		return(nIdxWin);
	}

	void SetIsShowRealResult( BOOL bShow )
	{
		m_bShowRealResult = bShow;
	}

	BOOL GetIsShowRealResult( )
	{
		return m_bShowRealResult;
	}

	void SetIsSaveImg( BOOL bSave )
	{
		m_bSaveImg = bSave;
	}

	BOOL GetIsSaveImg( )
	{
		return m_bSaveImg;
	}

	void SetIsLoadOfflinePlate( BOOL bLoad )
	{
		m_bLoadOfflinePlate = bLoad;
	}

	BOOL GetIsLoadOfflinePlate( )
	{
		return m_bLoadOfflinePlate;
	}

	void SetIsSetOffline( BOOL bSet )
	{
		m_bSetOffline = bSet;
	}

	BOOL GetIsSetOffline( )
	{
		return m_bSetOffline;
	}

	void SetIsShowPlate( BOOL bShow )
	{
		m_bShowPlate = bShow;
	}

	BOOL GetIsShowPlate( )
	{
		return m_bShowPlate;
	}

	void SetImgSize( int nSize )
	{
		m_nImgSize = nSize;
	}

	int GetImgSize( )
	{
		return m_nImgSize;
	}

	void SetCurIO( int io )
	{
		m_nCurIO = io;
	}

	int GetCurIO( )
	{
		return m_nCurIO;
	}

	void SetCurSerialPort( int port )
	{
		m_nCurSerialPort = port;
	}

	int GetCurSerialPort( )
	{
		return m_nCurSerialPort;
	}

	void SetUserPwd(char *sUserPwd )
	{
		memset(m_sUserPwd, 0, sizeof(m_sUserPwd));
		strcpy_s(m_sUserPwd, sizeof(m_sUserPwd) - 1, sUserPwd);
	}

	char* GetUserPwd()
	{
		return m_sUserPwd;
	}

	void SetBoardType(int type)
	{
		m_nBoardType = type;
	}

	int GetBoardType()
	{
		return m_nBoardType;
	}


public:
	char strIP[256];
	char m_sUserPwd[32];
	VzLPRClientHandle hLPRClient;
	int nIdxWin;
	int nSerialHandle[2];
	std::string device_sn;

	BOOL m_bShowRealResult;
	BOOL m_bSaveImg;
	BOOL m_bLoadOfflinePlate;
	BOOL m_bSetOffline;
	BOOL m_bShowPlate;
	int m_nImgSize;
	int m_nCurIO;
	int m_nCurSerialPort;
	int m_nBoardType;
	int m_nCloudLogin;
	long m_nTalkHandle;
};