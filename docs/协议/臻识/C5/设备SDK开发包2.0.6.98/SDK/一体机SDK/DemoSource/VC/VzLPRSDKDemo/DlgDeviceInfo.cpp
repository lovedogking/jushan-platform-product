// DlgDeviceInfo.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgDeviceInfo.h"


// DlgDeviceInfo dialog

IMPLEMENT_DYNAMIC(DlgDeviceInfo, CDialog)

DlgDeviceInfo::DlgDeviceInfo(CWnd* pParent /*=NULL*/)
	: CDialog(DlgDeviceInfo::IDD, pParent)
{

}

DlgDeviceInfo::~DlgDeviceInfo()
{
}

void DlgDeviceInfo::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
}


BEGIN_MESSAGE_MAP(DlgDeviceInfo, CDialog)
 
END_MESSAGE_MAP()


// DlgDeviceInfo message handlers

void DlgDeviceInfo::SetLPRHandle(VzLPRClientHandle hLPRClient)
{
      m_hLPRClient = hLPRClient;
}

BOOL  DlgDeviceInfo::OnInitDialog()
{
	CDialog::OnInitDialog();

	InitDevInfo();	
	return TRUE;
}
BOOL  DlgDeviceInfo::InitDevInfo()
{
	//设备序列号
	VZ_DEV_SERIAL_NUM struSN = {0};
	int retSN = VzLPRClient_GetSerialNumber(m_hLPRClient,&struSN);
	if (retSN < 0 )
	{
		MessageBox("获取设备序列号失败");
		return TRUE;
	}
	char strSN[64];
	sprintf_s(strSN,"%08X-%08X",struSN.uHi,struSN.uLo);
	SetDlgItemText(IDC_EDIT_SERIAL_NUM,strSN);
    
	//软件版本
	int ver1 = 0,ver2 = 0, ver3 = 0,ver4 = 0;
    int retSV = VzLPRClient_GetRemoteSoftWareVersion(m_hLPRClient,&ver1,&ver2,&ver3,&ver4);
	if (retSV < 0)
	{
		MessageBox("获取软件版本号失败");
		return TRUE;
	}
	char strSV[64];
	sprintf_s(strSV,"%d.%d.%d.%d",ver1,ver2,ver3,ver4);
	SetDlgItemText(IDC_EDIT_SOFTWARE_VERSION,strSV);

	//系统版本
	char pstrVersion[64] = " ";
	int retPSV = VzLPRClient_GetSystemVersion(m_hLPRClient,pstrVersion,sizeof(pstrVersion));
	if (retPSV < 0)
	{
		MessageBox("获取系统版本号失败");
		return TRUE;
	}
	SetDlgItemText(IDC_EDIT_SYS_VERSION,pstrVersion);

	//硬件信息
	int status = 0;
	char mac[20];
	char serialno[20];
	int device_type = 0;
	VZ_FS_INFO_EX data_ex = {0};

	int ret = VzLPRClient_GetHwInfo(m_hLPRClient, &status, mac, serialno, &device_type, &data_ex );

    if (0 == status)
    {
		MessageBox("没有获得硬件信息");
		return TRUE;
    }
	SetDlgItemText(IDC_EDIT_MAC,mac);

	int verion = 0;
	long long exdataSize = 0;
	int ret_hw = VzLPRClient_GetHwBoardVersion(m_hLPRClient, &verion, &exdataSize);
	if( ret_hw == 0 )
	{
		char version_info[32] = {0};
		sprintf_s(version_info, sizeof(version_info), "%d", verion);
		SetDlgItemText(IDC_EDIT_BOARD_VERSION,version_info);

		char exdata_info[32] = {0};
		sprintf_s(exdata_info, sizeof(exdata_info), "%I64d", exdataSize);
		SetDlgItemText(IDC_EDIT_EXDATA,exdata_info);
	}

	char oem_info[32] = {0};
	char company_info[32] = {0};
	VzLPRClient_GetOEMInfo( m_hLPRClient, oem_info, 32, company_info, 32 );

	SetDlgItemText(IDC_EDIT_MF_INFO,company_info);
	
	GetModuleFileName(NULL, m_strModuleDir, MAX_PATH);
	*strrchr(m_strModuleDir, '\\') = 0;

	char strCompaniesPath[MAX_PATH] = {0};
	sprintf_s(strCompaniesPath, MAX_PATH, "%s\\Companies.txt", m_strModuleDir);

	CString strMsg;

	FILE *fp = NULL;
	fp = fopen(strCompaniesPath, "r+");

	if( fp == NULL )
	{
		// MessageBox("请检查当前目录中是否存在\"Companies.txt\"文件！");
		return TRUE;
	}

	char szName[256] = {0};
	int index = 0;

	while ( fgets (szName , 256 , fp) != NULL )
	{
		if( index == data_ex.oem_info[2] )
		{
			strMsg = szName;
			break;
		}

		memset(szName, 0, sizeof(szName));
		index++;
	}

	fclose( fp );

	if( strMsg.GetLength() > 0 )
	{
		SetDlgItemText(IDC_EDIT_MF_INFO,strMsg);
	}
	//else
	//{
	//	MessageBox("未获取到OEM信息", "提示", MB_OK);
	//}

	

	return TRUE;
}

 
