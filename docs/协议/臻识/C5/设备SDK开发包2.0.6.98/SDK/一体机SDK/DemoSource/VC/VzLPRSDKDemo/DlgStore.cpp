// DlgStore.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgStore.h"

#include "VzLPRClientSDK.h"
#include <VzLPRFaceClientSDK.h>
// CDlgStore dialog

#define ID_TIME_GET_STORE_DEVICE  100001

IMPLEMENT_DYNAMIC(CDlgStore, CDialog)

CDlgStore::CDlgStore(CWnd* pParent /*=NULL*/)
: CDialog(CDlgStore::IDD, pParent)
{
	m_hLPRClient = NULL;
	m_SelectStoreDev = -1;
	memset(&m_sdi, 0, sizeof(m_sdi));
}

CDlgStore::~CDlgStore()
{
	//
}

void CDlgStore::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_LIST_STROE_DEV, m_ListStoreDevPartion);
	DDX_Control(pDX, IDC_COMBO_STORE_NUM, m_CbStoreNum);
}


BEGIN_MESSAGE_MAP(CDlgStore, CDialog)
	ON_BN_CLICKED(IDC_BUTTON_FOMART_SD, &CDlgStore::OnBnClickedButtonFomartSd)
	ON_CBN_SELCHANGE(IDC_COMBO_STORE_NUM, &CDlgStore::OnCbnSelchangeComboStoreNum)
	ON_WM_TIMER()
END_MESSAGE_MAP()


// CDlgStore message handlers
void CDlgStore::SetLPRHandle(VzLPRClientHandle hLPRClient)
{
	m_hLPRClient = hLPRClient;
}

BOOL CDlgStore::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here


	LVCOLUMN lc;

	lc.mask = LVCF_TEXT | LVCF_WIDTH;
	lc.pszText = "分区号";
	lc.cx = 50;
	lc.cchTextMax = strlen("分区号");
	m_ListStoreDevPartion.InsertColumn(0, &lc);


	lc.pszText = "分区状态";
	lc.cx = 70;
	lc.cchTextMax = strlen("分区状态");
	m_ListStoreDevPartion.InsertColumn(1, &lc);

	lc.pszText = "已用空间(单位:M)";
	lc.cx = 120;
	lc.cchTextMax = strlen("已用空间(单位:M)");
	m_ListStoreDevPartion.InsertColumn(2, &lc);

	lc.pszText = "可用空间(单位:M)";
	lc.cx = 120;
	lc.cchTextMax = strlen("可用空间(单位:M)");
	m_ListStoreDevPartion.InsertColumn(3, &lc);

	lc.pszText = "总容量(单位:M)";
	lc.cx = 100;
	lc.cchTextMax = strlen("总容量(单位:M)");
	m_ListStoreDevPartion.InsertColumn(4, &lc);

	lc.pszText = "格式化百分比";
	lc.cx = 100;
	lc.cchTextMax = strlen("格式化百分比");
	m_ListStoreDevPartion.InsertColumn(5, &lc);
    
	if(GetStoreDeviceInfo())
	{
        UpdateControl();
	}

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}


void CDlgStore::OnBnClickedButtonFomartSd()
{
	// TODO: Add your control notification handler code here
	if (m_hLPRClient)
	{
		if (GetStoreDeviceInfo())
		{
			int ret = VzLPRClient_SDFormat(m_hLPRClient);
			if (ret != 0)
			{
				MessageBox("格式化失败");
			}
			else
			{
				MessageBox("格式化完成");
			}
		}
	}
}


void CDlgStore::SelectStoreDev( int iNum)
{ 
	if (iNum >= 0 && iNum < m_sdi.nDevCount )
	{ 
		if (m_sdi.struSDI[iNum].eType == VZ_STORAGE_DEV_TYPE::VZ_STORAGE_DEV_HD)
		{
			SetDlgItemText(IDC_EDIT_STORE_TYPE, "硬盘");  
		}
		else{
			SetDlgItemText(IDC_EDIT_STORE_TYPE, "SD卡");
		}


		if (m_sdi.struSDI[iNum ].eStatus == VZ_STORAGE_DEV_STATUS::VZ_STORAGE_DEV_NO_PART )
		{
			SetDlgItemText(IDC_EDIT_STATUS, "未分区");
		}
		else{
			SetDlgItemText(IDC_EDIT_STATUS, "已分区");
		}


		char strtemp[50];

		memset(strtemp, 0, 50);

		LVITEM lv;

		m_ListStoreDevPartion.DeleteAllItems();

		for (int i = 0; i < m_sdi.struSDI[iNum ].uPartNum; i++)
		{

			//分区号
			sprintf_s(strtemp, 50, "%d", i+1);
			lv.mask = LVIF_TEXT;
			lv.iItem = i;
			lv.iSubItem = 0;
			lv.pszText = strtemp;
			lv.cchTextMax = strlen(strtemp);
			m_ListStoreDevPartion.InsertItem((&lv));

			//分区状态
			if (VZ_STORAGE_DEV_PART_STATUS::VZ_STORAGE_DEV_PART_ERROR == m_sdi.struSDI[iNum ].struPartInfo[i].eStatus)
			{
				sprintf_s(strtemp, 50, "%s", "异常");
			}
			else if (VZ_STORAGE_DEV_PART_STATUS::VZ_STORAGE_DEV_PART_NOT_FORMAT == m_sdi.struSDI[iNum ].struPartInfo[i].eStatus)
			{
				sprintf_s(strtemp, 50, "%s", "未格式化");
			}
			else if (VZ_STORAGE_DEV_PART_STATUS::VZ_STORAGE_DEV_PART_NOT_MOUNT == m_sdi.struSDI[iNum ].struPartInfo[i].eStatus)
			{
				sprintf_s(strtemp, 50, "%s", "未挂载");
			}
			else if (VZ_STORAGE_DEV_PART_STATUS::VZ_STORAGE_DEV_PART_FORMATING == m_sdi.struSDI[iNum    ].struPartInfo[i].eStatus)
			{
				sprintf_s(strtemp, 50, "%s", "正在格式化");
			}
			else if (VZ_STORAGE_DEV_PART_STATUS::VZ_STORAGE_DEV_PART_DELETING == m_sdi.struSDI[iNum    ].struPartInfo[i].eStatus)
			{
				sprintf_s(strtemp, 50, "%s", "正在删除文件");
			}
			else if (VZ_STORAGE_DEV_PART_STATUS::VZ_STORAGE_DEV_PART_NORMAL == m_sdi.struSDI[iNum    ].struPartInfo[i].eStatus)
			{
				sprintf_s(strtemp, 50, "%s", "正常工作");
			}


			lv.iSubItem = 1;
			lv.pszText = strtemp;
			lv.cchTextMax = strlen(strtemp);
			m_ListStoreDevPartion.SetItem((&lv));


			//已用空间
			sprintf_s(strtemp, 50, "%d", m_sdi.struSDI[iNum    ].struPartInfo[i].uUsed);
			lv.iSubItem = 2;
			lv.pszText = strtemp;
			lv.cchTextMax = strlen(strtemp);
			m_ListStoreDevPartion.SetItem((&lv));

			//可用空间
			sprintf_s(strtemp, 50, "%d", m_sdi.struSDI[iNum    ].struPartInfo[i].uLeft);
			lv.iSubItem = 3;
			lv.pszText = strtemp;
			lv.cchTextMax = strlen(strtemp);
			m_ListStoreDevPartion.SetItem((&lv));

			//总空间
			sprintf_s(strtemp, 50, "%d", m_sdi.struSDI[iNum    ].struPartInfo[i].uTotal);
			lv.iSubItem = 4;
			lv.pszText = strtemp;
			lv.cchTextMax = strlen(strtemp);
			m_ListStoreDevPartion.SetItem((&lv));

			//格式化百分比
			sprintf_s(strtemp, 50, "%d", m_sdi.struSDI[iNum    ].struPartInfo[i].nFormatPercent);
			lv.iSubItem = 5;
			lv.pszText = strtemp;
			lv.cchTextMax = strlen(strtemp);
			m_ListStoreDevPartion.SetItem((&lv));
		}

	}
}

void CDlgStore::OnCbnSelchangeComboStoreNum()
{
	// TODO: Add your control notification handler code here

	int iSel = m_CbStoreNum.GetCurSel();

	if (iSel != CB_ERR )
	{
		m_SelectStoreDev = iSel;
		SelectStoreDev(m_SelectStoreDev);
	}
}

bool   CDlgStore::GetStoreDeviceInfo()
{
	if (m_hLPRClient)
	{
		if (VzLPRClient_GetStorageDeviceInfo(m_hLPRClient, &m_sdi) != 0)
		{
			MessageBox("设备无SD卡，请插入SD卡后，使用该功能");
		}
		else
		{
			return true;
		}
	}

    return false;
}


void   CDlgStore::UpdateControl()
{

	if(m_sdi.nDevCount > 0)
	{
		char strtemp[50];
		memset(strtemp, 0, 50);


		sprintf_s(strtemp, 50, "%d", m_sdi.nDevCount);
		SetDlgItemText(IDC_STATIC_STORE_SIZE, strtemp);

		m_CbStoreNum.ResetContent();
		for (int i = 0; i < m_sdi.nDevCount; i++)
		{
			sprintf_s(strtemp, 50, "%d", i+1);

			m_CbStoreNum.AddString(strtemp);
		}
		if (m_SelectStoreDev == -1 || m_sdi.nDevCount < m_SelectStoreDev)
		{
			m_SelectStoreDev = 0;
		}

		m_CbStoreNum.SetCurSel(m_SelectStoreDev);
		SelectStoreDev(m_SelectStoreDev);


		int iFormatNum = 0 ;

		for (int i = 0; i < m_sdi.struSDI[m_SelectStoreDev].uPartNum; i++)
		{
			if(  m_sdi.struSDI[m_SelectStoreDev].struPartInfo[i].eStatus ==  VZ_STORAGE_DEV_PART_STATUS::VZ_STORAGE_DEV_PART_FORMATING)
			{
				iFormatNum++;
			}
		}

		if(iFormatNum > 0)
		{
			GetDlgItem(IDC_BUTTON_FOMART_SD)->EnableWindow(FALSE);
		}
		else
		{
			GetDlgItem(IDC_BUTTON_FOMART_SD)->EnableWindow(TRUE);
		}
	}

}