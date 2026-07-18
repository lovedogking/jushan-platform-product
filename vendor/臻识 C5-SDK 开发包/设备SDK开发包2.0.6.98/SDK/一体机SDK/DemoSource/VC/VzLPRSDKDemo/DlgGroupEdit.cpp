// DlgGroupEdit.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgGroupEdit.h"
#include "Base64.h"
#include "VzString.h"

#include <string>
#include "VzLPRSDKDemoDlg.h"
using namespace std;

// CDlgGroupEdit dialog

IMPLEMENT_DYNAMIC(CDlgGroupEdit, CDialog)

CDlgGroupEdit::CDlgGroupEdit(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgGroupEdit::IDD, pParent)
	, m_strName(_T(""))
	, m_bEnable(FALSE)
	, m_strGarage(_T(""))
	, m_strMatchDevice(_T(""))
	, m_strNextGrage(_T(""))
	, m_bDGResult(FALSE)
{
	m_nType = 0;
	m_hLPRClient = NULL;
	m_hMainWnd = NULL;

	memset(&m_data, 0, sizeof(VZ_OVZID_RESULT));
	m_nResultIndex = 0;
}

CDlgGroupEdit::~CDlgGroupEdit()
{
}

void CDlgGroupEdit::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Text(pDX, IDC_EDIT_NAME, m_strName);
	DDX_Control(pDX, IDC_IP_DEVICE, m_ipCtrl);
	DDX_Control(pDX, IDC_CMB_TYPE, m_cmbType);
	DDX_Check(pDX, IDC_CHK_ENABLE, m_bEnable);
	DDX_Text(pDX, IDC_EDIT_GARAGE, m_strGarage);
	DDX_Text(pDX, IDC_EDIT_MATCH_DEVICE, m_strMatchDevice);
	DDX_Text(pDX, IDC_EDIT_NEXT_GARAGE, m_strNextGrage);
	DDX_Check(pDX, IDC_CHK_DGRESULT_ENABLE, m_bDGResult);
}


BEGIN_MESSAGE_MAP(CDlgGroupEdit, CDialog)
	ON_BN_CLICKED(IDOK, &CDlgGroupEdit::OnBnClickedOk)
	ON_BN_CLICKED(IDC_CHK_DGRESULT_ENABLE, &CDlgGroupEdit::OnBnClickedChkDgresultEnable)
END_MESSAGE_MAP()


// CDlgGroupEdit message handlers

BOOL CDlgGroupEdit::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_cmbType.AddString("未组网");
	m_cmbType.AddString("入口");
	m_cmbType.AddString("出口");

	m_cmbType.SetCurSel(m_nType);

	string sGroupName = m_strGroupName.GetBuffer(0);

	if( sGroupName.length() > 0 )
	{
		string value = "/";
		std::vector<std::string> names;
		CVzString::split(sGroupName, value, &names);

		if ( names.size() > 1)
		{
			m_strGarage = names[0].c_str();
			if( names.size() >= 3 )
			{
				m_strMatchDevice = names[1].c_str( );

				//if( names.size() >= 4 )
				//{
				//	m_strMatchDevice = names[2].c_str();
				//}
			}
		}
	}

	m_ipCtrl.SetWindowText(m_strIP);
	UpdateData(FALSE);

	return TRUE;
}

void CDlgGroupEdit::SetDeviceInfo(CString strName, CString strIP, int nType, CString strSerialNO, CString strGroupName, BOOL bEnable)
{
	m_strName	= strName;
	m_strIP		= strIP;
	m_nType		= nType;

	m_strSerialNO	= strSerialNO;
	m_strGroupName	= strGroupName;
	m_bEnable		= bEnable;
}

int __stdcall OnPlateGroupInfo(VzLPRClientHandle handle, 
							   void *pUserData,int exitEntryInfo,const TH_PlateResult *exitIvsInfo,
							   const TH_PlateResult *entryIvsInfo, const IVS_DG_DEVICE_INFO *exitDGInfo, 
							   const IVS_DG_DEVICE_INFO *entryDGInfo)
{
   //CVzLPRSDKDemoDlg *pInstance = (CVzLPRSDKDemoDlg *)pUserData;

   //SetNotifyCommStr(exitEntryInfo,exitIvsInfo,entryIvsInfo);
   char cMsg[128];
   memset(cMsg, '/0', 128);
   if(0 == exitEntryInfo){

	   sprintf_s(cMsg,"VZLPRC_PLATE_GROUP_INFO_CALLBACK【入口设备】 车牌号：%s",entryIvsInfo->license); 
   }

   else if(1 == exitEntryInfo){

	   sprintf_s(cMsg,"VZLPRC_PLATE_GROUP_INFO_CALLBACK【出口设备】 车牌号：%s",entryIvsInfo->license); 
   }

   string* sNewMsg = new string;
   sNewMsg->append(cMsg, strlen(cMsg));

   PostMessage(HWND(pUserData), WM_SHOWMSG, (WPARAM)NULL, (LPARAM)sNewMsg);

   return(0);
}

// 保存组网配置
void CDlgGroupEdit::OnBnClickedOk()
{
	UpdateData(TRUE);

	if( m_strGarage == "" )
	{
		MessageBox("车库名称不能为空", "提示", MB_OK );
		return;
	}

	if ( m_strName == "" )
	{
		MessageBox("设备名称不能为空", "提示", MB_OK );
		return;
	}

	if ( m_hLPRClient != NULL )
	{
		CString strIP;
		m_ipCtrl.GetWindowText(strIP);

		// 主库
		string sGroupPath;
		char *name = CVzString::EncodeStr(m_strGarage.GetBuffer(0));
		if ( name != NULL )
		{
			sGroupPath.append( name );
			sGroupPath.append("&");

			delete[] name;
			name = NULL;
		}

		// 子库
		//if( m_strNextGrage.GetLength() > 0 )
		//{
		//	char *nextGrage = CVzString::EncodeStr(m_strNextGrage.GetBuffer(0));
		//	if ( nextGrage != NULL )
		//	{
		//		sGroupPath.append( nextGrage );
		//		sGroupPath.append("&");

		//		delete[] nextGrage;
		//		nextGrage = NULL;
		//	}
		//}
		
		// 相辅设备
		if( m_strMatchDevice.GetLength() > 0 )
		{
			char *matchDevice = CVzString::EncodeStr(m_strMatchDevice.GetBuffer(0));
			if ( matchDevice != NULL )
			{
				sGroupPath.append( matchDevice );
				sGroupPath.append("&");

				delete[] matchDevice;
				matchDevice = NULL;
			}
		}

		char *lastName = CVzString::EncodeStr(m_strName.GetBuffer(0));
		if ( lastName != NULL )
		{
			sGroupPath.append( lastName );

			delete[] lastName;
			lastName = NULL;

			sGroupPath.append("#");
		}
		
		int index = m_nResultIndex;

		string sOriginalName = m_data.info[index].original_name;
		string sLastName;

		int nPos = sOriginalName.find_last_of("#");
		if ( nPos >= 0 && nPos < sOriginalName.length() )
		{
			sLastName = sOriginalName.substr(nPos + 1, sOriginalName.length());
			sGroupPath.append(sLastName);
		}

		m_data.info[index].enable_group = m_bEnable;
		strcpy(m_data.info[index].ip_addr, strIP.GetBuffer(0));
		strcpy(m_data.info[index].sn, m_strSerialNO.GetBuffer(0));
		strcpy(m_data.info[index].original_name, sGroupPath.c_str() );
		m_data.info[index].type  = m_cmbType.GetCurSel();

		int ret = 0; // VzLPRClient_EnableDevicegroup(m_hLPRClient, &m_data);

		//开启识别回调
		if (ret == 0 && m_bDGResult) {
 			VzLPRClient_SetDGResultEnable(m_hLPRClient, true);
			VzLPRClient_SetPlateGroupInfoCallBack(m_hLPRClient, OnPlateGroupInfo, m_hMainWnd);
		}
		else {
			VzLPRClient_SetPlateGroupInfoCallBack(m_hLPRClient, NULL, NULL);
		}
	}

	OnOK();
}

void CDlgGroupEdit::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

void CDlgGroupEdit::setMainHandle(HWND hWnd)
{
	m_hMainWnd = hWnd;
}

void CDlgGroupEdit::SetOvzidResult(VZ_OVZID_RESULT *pData, int nIndex)
{
	if ( pData != NULL )
	{
		memcpy(&m_data, pData, sizeof(VZ_OVZID_RESULT));
	}

	m_nResultIndex = nIndex;
}
void CDlgGroupEdit::OnBnClickedChkDgresultEnable()
{
	// TODO: Add your control notification handler code here
}
