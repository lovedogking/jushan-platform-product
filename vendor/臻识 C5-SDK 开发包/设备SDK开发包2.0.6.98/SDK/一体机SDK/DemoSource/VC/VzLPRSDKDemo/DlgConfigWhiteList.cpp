// DlgConfigWhiteList.cpp : implementation file
//
#include "stdafx.h"
#if 0
#include "VzLPRSDKDemo.h"
#include "DlgConfigWhiteList.h"

const unsigned g_LPRecPageSize = 8;
const unsigned g_CustomerPageSize = 8;

// DlgConfigWhiteList dialog

IMPLEMENT_DYNAMIC(DlgConfigWhiteList, CDialog)

DlgConfigWhiteList::DlgConfigWhiteList(VzLPRClientHandle handle, CWnd* pParent /*=NULL*/)
	: CDialog(DlgConfigWhiteList::IDD, pParent)
	, m_hLPRC(handle)
	, m_pLPRecords(new VZ_LPR_WLIST_VEHICLE[g_LPRecPageSize]), m_uLPRecNum(0)
	, m_uCurrLPRecPageNum(0)
	, m_pLPCustomers(new VZ_LPR_WLIST_CUSTOMER[g_CustomerPageSize])
	, m_uCustomerNum(0), m_uCurrCustomerPageNum(0)
{

}

DlgConfigWhiteList::~DlgConfigWhiteList()
{
	delete []m_pLPRecords;
}

void DlgConfigWhiteList::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_LIST_CUSTOMER, m_cListCustomer);
	DDX_Control(pDX, IDC_LIST_LP_REC, m_cListLPRec);
}


BEGIN_MESSAGE_MAP(DlgConfigWhiteList, CDialog)
	ON_BN_CLICKED(IDC_BTN_SAVE_LP, &DlgConfigWhiteList::OnBnClickedBtnSaveLp)
	ON_BN_CLICKED(IDC_BTN_REFRESH_PAGE_LP, &DlgConfigWhiteList::OnBnClickedBtnRefreshPageLp)
	ON_BN_CLICKED(IDC_BTN_NEW_LP, &DlgConfigWhiteList::OnBnClickedBtnNewLp)
	ON_BN_CLICKED(IDC_BTN_NEW_CUSTOMER, &DlgConfigWhiteList::OnBnClickedBtnNewCustomer)
	ON_BN_CLICKED(IDC_BTN_SAVE_CUSTOMER, &DlgConfigWhiteList::OnBnClickedBtnSaveCustomer)
	ON_BN_CLICKED(IDC_BTN_REFRESH_PAGE_CUSTOMER, &DlgConfigWhiteList::OnBnClickedBtnRefreshPageCustomer)
	ON_BN_CLICKED(IDC_BTN_PREV_PAGE_CUSTOMER, &DlgConfigWhiteList::OnBnClickedBtnPrevPageCustomer)
	ON_BN_CLICKED(IDC_BTN_NEXT_PAGE_CUSTOMER, &DlgConfigWhiteList::OnBnClickedBtnNextPageCustomer)
	ON_BN_CLICKED(IDC_BTN_DEL_CUSTOMER, &DlgConfigWhiteList::OnBnClickedBtnDelCustomer)
	ON_NOTIFY(LVN_ITEMCHANGED, IDC_LIST_CUSTOMER, &DlgConfigWhiteList::OnLvnItemchangedListCustomer)
	ON_NOTIFY(LVN_ITEMCHANGED, IDC_LIST_LP_REC, &DlgConfigWhiteList::OnLvnItemchangedListLpRec)
	ON_BN_CLICKED(IDC_BTN_SET_CUST_ID, &DlgConfigWhiteList::OnBnClickedBtnSetCustId)
	ON_BN_CLICKED(IDC_BTN_PREV_PAGE_LP, &DlgConfigWhiteList::OnBnClickedBtnPrevPageLp)
	ON_BN_CLICKED(IDC_BTN_NEXT_PAGE_LP, &DlgConfigWhiteList::OnBnClickedBtnNextPageLp)
	ON_BN_CLICKED(IDC_BTN_DEL_LP, &DlgConfigWhiteList::OnBnClickedBtnDelLp)
	ON_BN_CLICKED(IDC_BTN_LOOKUP_LP, &DlgConfigWhiteList::OnBnClickedBtnLookupLp)
	ON_BN_CLICKED(IDC_BTN_LOOKUP_CUST, &DlgConfigWhiteList::OnBnClickedBtnLookupCust)
END_MESSAGE_MAP()


void __stdcall CALLBACK_ON_QUERY_LP_RECORD(VZLPRC_WLIST_CB_TYPE type,const VZ_LPR_WLIST_VEHICLE *pLP, 
										   const VZ_LPR_WLIST_CUSTOMER *pCustomer,
										   void *pUserData)
{
	DlgConfigWhiteList *pInstance = (DlgConfigWhiteList *)pUserData;
	pInstance->iOnQueryLPRecord(type,pLP, pCustomer);
}

void DlgConfigWhiteList::iOnQueryLPRecord(VZLPRC_WLIST_CB_TYPE type,const VZ_LPR_WLIST_VEHICLE *pLP,
										  const VZ_LPR_WLIST_CUSTOMER *pCustomer)
{
	if(pLP)
	{
		if(m_uLPRecNum == g_LPRecPageSize)
			return;

		char str[64];

		sprintf_s(str, 64, "%s", pLP->strPlateID);
		m_cListLPRec.InsertItem(m_uLPRecNum, str);
		
		sprintf_s(str, 64, "%u", pLP->bEnable);
		m_cListLPRec.SetItemText(m_uLPRecNum, 0, str);
		
		m_cListLPRec.SetItemText(m_uLPRecNum, 1, pLP->strPlateID);
		
		sprintf_s(str, 64, "%04d-%02d-%02d %02d:%02d:%02d", 
			pLP->struTMOverdule.nYear, pLP->struTMOverdule.nMonth, pLP->struTMOverdule.nMDay,
			pLP->struTMOverdule.nHour, pLP->struTMOverdule.nMin, pLP->struTMOverdule.nSec);
		m_cListLPRec.SetItemText(m_uLPRecNum, 2, str);
		
		sprintf_s(str, 64, "%u", pLP->bUsingTimeSeg);
		m_cListLPRec.SetItemText(m_uLPRecNum, 3, str);
		
		sprintf_s(str, 64, "%u", pLP->bAlarm);
		m_cListLPRec.SetItemText(m_uLPRecNum, 4, str);
		
		sprintf_s(str, 64, "%u", pLP->uCustomerID);
		m_cListLPRec.SetItemText(m_uLPRecNum, 5, str);

		memcpy(m_pLPRecords + m_uLPRecNum, pLP, sizeof(VZ_LPR_WLIST_VEHICLE));
		m_uLPRecNum++;
	}
	
	if(pCustomer)
	{
		if(m_uCustomerNum == g_CustomerPageSize)
			return;

		char str[32];
		sprintf_s(str, 32, "%u", pCustomer->uCustomerID);
		m_cListCustomer.InsertItem(m_uCustomerNum, str);
		m_cListCustomer.SetItemText(m_uCustomerNum, 1, pCustomer->strName);
		m_cListCustomer.SetItemText(m_uCustomerNum, 2, pCustomer->strCode);
		
		memcpy(m_pLPCustomers + m_uCustomerNum, pCustomer, sizeof(VZ_LPR_WLIST_CUSTOMER));
		m_uCustomerNum++;
	}
}

// DlgConfigWhiteList message handlers

BOOL DlgConfigWhiteList::OnInitDialog()
{
	CDialog::OnInitDialog();

	// TODO:  Add extra initialization here
	//初始化车牌信息列表
	m_cListLPRec.ModifyStyle(0, LVS_REPORT);
	m_cListLPRec.SetExtendedStyle(m_cListCustomer.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);
	m_cListLPRec.InsertColumn(0, "启用");
	m_cListLPRec.InsertColumn(1, "车牌号");
	m_cListLPRec.InsertColumn(2, "过期时间");
	m_cListLPRec.InsertColumn(3, "启用时间段");
	m_cListLPRec.InsertColumn(4, "黑名单");
	m_cListLPRec.InsertColumn(5, "车主编号");
	CRect rect; 
	m_cListLPRec.GetClientRect(rect);
	int nUnitW = rect.Width()/24;
	m_cListLPRec.SetColumnWidth(0, nUnitW*2);
	m_cListLPRec.SetColumnWidth(1, nUnitW*2);
	m_cListLPRec.SetColumnWidth(2, nUnitW*4);
	m_cListLPRec.SetColumnWidth(3, nUnitW*6);
	m_cListLPRec.SetColumnWidth(4, nUnitW*4);
	m_cListLPRec.SetColumnWidth(5, nUnitW*3);
	m_cListLPRec.SetColumnWidth(6, nUnitW*3);

	//初始化用户列表
	m_cListCustomer.ModifyStyle(0, LVS_REPORT);
	m_cListCustomer.SetExtendedStyle(m_cListCustomer.GetExtendedStyle() | LVS_EX_GRIDLINES | LVS_EX_FULLROWSELECT);
	m_cListCustomer.InsertColumn(0, "车主编号");
	m_cListCustomer.InsertColumn(1, "姓名");
	m_cListCustomer.InsertColumn(2, "身份码");

	m_cListCustomer.GetClientRect(rect);
	nUnitW = rect.Width()/10;
	m_cListCustomer.SetColumnWidth(0, nUnitW*2);
	m_cListCustomer.SetColumnWidth(1, nUnitW*2);
	m_cListCustomer.SetColumnWidth(2, nUnitW*6);

	VzLPRClient_WhiteListSetQueryCallBack(m_hLPRC, CALLBACK_ON_QUERY_LP_RECORD, this);

	iQueryLPRecPage();
	iQueryCustomerPage();

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}

BOOL DlgConfigWhiteList::DestroyWindow()
{
	// TODO: Add your specialized code here and/or call the base class

	return CDialog::DestroyWindow();
}

static int iListCtrlGetCurSel(CListCtrl *pList)
{
	int nCurrSel = -1;
	POSITION pos = pList->GetFirstSelectedItemPosition();
	while(pos)
	{
		nCurrSel = pList->GetNextSelectedItem(pos);
	}

	return(nCurrSel);
}

void DlgConfigWhiteList::iQueryLPRecPage()
{
	m_uLPRecNum = 0;

	((CListCtrl *)GetDlgItem(IDC_LIST_LP_REC))->DeleteAllItems();

	VzLPRClient_WhiteListLoadVehicleRange(m_hLPRC,
		m_uCurrLPRecPageNum*g_LPRecPageSize, g_LPRecPageSize,NULL,0,NULL);

	iCleanLPRecEdit();
}

void DlgConfigWhiteList::OnBnClickedBtnSaveLp()
{
	VZ_LPR_WLIST_VEHICLE rec = {0};
	//首先取原来的数据，以防其中有未被设置的数据被清空
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_LP_REC);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel >= 0 && nCurrSel < (int)m_uLPRecNum)
	{
		memcpy(&rec, &m_pLPRecords[nCurrSel], sizeof(VZ_LPR_WLIST_VEHICLE));
	}

	rec.bEnable = ((CButton *)GetDlgItem(IDC_CHK_LP_ENABLE))->GetCheck() == BST_CHECKED ? 1 : 0;
	GetDlgItemText(IDC_EDIT_REC_LP, rec.strPlateID, VZ_LPR_WLIST_LP_MAX_LEN);

	//========更新时间=============
	CTime cTime;
	((CDateTimeCtrl *)GetDlgItem(IDC_DATE_PICK_OVERDULE))->GetTime(cTime);
	rec.struTMOverdule.nYear = cTime.GetYear();
	rec.struTMOverdule.nMonth = cTime.GetMonth();
	rec.struTMOverdule.nMDay = cTime.GetDay();
	((CDateTimeCtrl *)GetDlgItem(IDC_TIME_PICK_OVERDULE))->GetTime(cTime);
	rec.struTMOverdule.nHour = cTime.GetHour();
	rec.struTMOverdule.nMin = cTime.GetMinute();
	rec.struTMOverdule.nSec = cTime.GetSecond();

	rec.bAlarm = ((CButton *)GetDlgItem(IDC_CHK_LP_ALARM))->GetCheck() == BST_CHECKED ? 1 : 0;

	char strTemp[32];
	//========检查车主编号============
	GetDlgItemText(IDC_EDIT_REC_LP_CUST_ID, strTemp, 32);
	if(strTemp[0] == 0)
	{
		MessageBox("请选择一个车主");
		return;
	}
	rec.uCustomerID = atoi(strTemp);

	//========判断是新增还是更新记录
	GetDlgItemText(IDC_EDIT_REC_ID, strTemp, 32);
	if(strTemp[0] == '-')
	{
		if(VzLPRClient_WhiteListInsertVehicle(m_hLPRC, &rec) < 0)
		{
			MessageBox("新建记录失败");
		}
		else
		{
			//成功添加记录，如果本页显示满了，则需要更新下一页，以显示刚添加的记录
			if(m_uLPRecNum == g_CustomerPageSize)
				m_uCurrLPRecPageNum++;
		}
	}
	else
	{
		if(VzLPRClient_WhiteListUpdateVehicle(m_hLPRC, &rec) < 0)
		{
			MessageBox("保存记录失败");
		}
	}

	iQueryLPRecPage();
}

void DlgConfigWhiteList::OnBnClickedBtnRefreshPageLp()
{
	iQueryLPRecPage();
}

void DlgConfigWhiteList::iCleanLPRecEdit()
{
	//设置新增标识
	SetDlgItemText(IDC_EDIT_REC_ID, "-");
	SetDlgItemText(IDC_EDIT_REC_LP, "");
}

void DlgConfigWhiteList::OnBnClickedBtnNewLp()
{
	iCleanLPRecEdit();
	((CButton *)GetDlgItem(IDC_CHK_LP_ENABLE))->SetCheck(BST_CHECKED);
	GetDlgItem(IDC_EDIT_REC_LP)->SetFocus();
}

void DlgConfigWhiteList::iQueryCustomerPage()
{
	m_uCustomerNum = 0;

	((CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER))->DeleteAllItems();

	VzLPRClient_WhiteListLoadCustomerRange(m_hLPRC,
		m_uCurrCustomerPageNum*g_CustomerPageSize, g_CustomerPageSize,NULL,0,NULL);

	iCleanCustomerEdit();
}

void DlgConfigWhiteList::iCleanCustomerEdit()
{
	//设置新增标识
	SetDlgItemText(IDC_EDIT_CUSTOMER_ID, "-");
	SetDlgItemText(IDC_EDIT_CUSTOMER_NAME, "");
	SetDlgItemText(IDC_EDIT_CUSTOMER_CODE, "");
}

void DlgConfigWhiteList::OnBnClickedBtnNewCustomer()
{
	iCleanCustomerEdit();
	GetDlgItem(IDC_EDIT_CUSTOMER_NAME)->SetFocus();
}

void DlgConfigWhiteList::OnBnClickedBtnSaveCustomer()
{
	VZ_LPR_WLIST_CUSTOMER cust = {0};
	//首先取原来的数据，以防其中有未被设置的数据被清空
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel >= 0 && nCurrSel < (int)m_uCustomerNum)
	{
		memcpy(&cust, &m_pLPCustomers[nCurrSel], sizeof(VZ_LPR_WLIST_CUSTOMER));
	}

	GetDlgItemText(IDC_EDIT_CUSTOMER_NAME, cust.strName, VZ_LPR_WLIST_CUSTOMER_NAME_LEN);
	GetDlgItemText(IDC_EDIT_CUSTOMER_CODE, cust.strCode, VZ_LPR_WLIST_CUSTOMER_CODE_LEN);

	char strTemp[32];
	//========判断是新增还是更新记录
	GetDlgItemText(IDC_EDIT_CUSTOMER_ID, strTemp, 32);
	if(strTemp[0] == '-')
	{
		if(VzLPRClient_WhiteListInsertCustomer(m_hLPRC, &cust) < 0)
		{
			MessageBox("新建记录失败");
		}
		else
		{
			//成功添加记录，如果本页显示满了，则需要更新下一页，以显示刚添加的记录
			if(m_uCustomerNum == g_CustomerPageSize)
				m_uCurrCustomerPageNum++;
		}
	}
	else
	{
		if(VzLPRClient_WhiteListUpdateCustomer(m_hLPRC, &cust) < 0)
		{
			MessageBox("保存记录失败");
		}
	}

	iQueryCustomerPage();//*/
}

void DlgConfigWhiteList::OnBnClickedBtnRefreshPageCustomer()
{
	iQueryCustomerPage();
}

void DlgConfigWhiteList::OnBnClickedBtnPrevPageCustomer()
{
	if(m_uCurrCustomerPageNum == 0)
		return;

	m_uCurrCustomerPageNum--;

	iQueryCustomerPage();
}

void DlgConfigWhiteList::OnBnClickedBtnNextPageCustomer()
{
	if(m_uCustomerNum < g_CustomerPageSize)
		return;
	
	m_uCurrCustomerPageNum++;

	iQueryCustomerPage();
}

void DlgConfigWhiteList::OnBnClickedBtnDelCustomer()
{
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 || nCurrSel >= (int)m_uCustomerNum)
	{
		return;
	}
	
	VzLPRClient_WhiteListDeleteCustomer(m_hLPRC, m_pLPCustomers[nCurrSel].uCustomerID);

	if(m_uCustomerNum == 1)
	{
		if(m_uCurrCustomerPageNum >= 1)
		{
			m_uCurrCustomerPageNum--;
		}
	}

	iQueryCustomerPage();
}

void DlgConfigWhiteList::OnLvnItemchangedListCustomer(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMLISTVIEW pNMLV = reinterpret_cast<LPNMLISTVIEW>(pNMHDR);
	// TODO: Add your control notification handler code here
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);

	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 || nCurrSel>=(int)m_uCustomerNum)
		return;

	const VZ_LPR_WLIST_CUSTOMER &Cust = m_pLPCustomers[nCurrSel];

	SetDlgItemInt(IDC_EDIT_CUSTOMER_ID, Cust.uCustomerID);
	SetDlgItemText(IDC_EDIT_CUSTOMER_NAME, Cust.strName);
	SetDlgItemText(IDC_EDIT_CUSTOMER_CODE, Cust.strCode);

	//同时更换车牌列表中的ID，以供车牌记录的新建和更新
	SetDlgItemInt(IDC_EDIT_REC_LP_CUST_ID, Cust.uCustomerID);

	*pResult = 0;
}

void DlgConfigWhiteList::OnLvnItemchangedListLpRec(NMHDR *pNMHDR, LRESULT *pResult)
{
	LPNMLISTVIEW pNMLV = reinterpret_cast<LPNMLISTVIEW>(pNMHDR);
	// TODO: Add your control notification handler code here
	
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_LP_REC);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 || nCurrSel >= (int)m_uLPRecNum)
	{
		return;
	}

	const VZ_LPR_WLIST_VEHICLE &Rec = m_pLPRecords[nCurrSel];

	//SetDlgItemInt(IDC_EDIT_REC_ID, Rec.uID);
	((CButton *)GetDlgItem(IDC_CHK_LP_ENABLE))->SetCheck(Rec.bEnable ? BST_CHECKED : BST_UNCHECKED);
	SetDlgItemText(IDC_EDIT_REC_LP, Rec.strPlateID);
	CTime cTime(Rec.struTMOverdule.nYear, Rec.struTMOverdule.nMonth, Rec.struTMOverdule.nMDay,
		Rec.struTMOverdule.nHour, Rec.struTMOverdule.nMin, Rec.struTMOverdule.nSec);
	((CDateTimeCtrl *)GetDlgItem(IDC_DATE_PICK_OVERDULE))->SetTime(&cTime);
	((CDateTimeCtrl *)GetDlgItem(IDC_TIME_PICK_OVERDULE))->SetTime(&cTime);
	((CButton *)GetDlgItem(IDC_CHK_LP_ALARM))->SetCheck(Rec.bAlarm ? BST_CHECKED : BST_UNCHECKED);
	SetDlgItemInt(IDC_EDIT_REC_LP_CUST_ID, Rec.uCustomerID);

	*pResult = 0;
}

void DlgConfigWhiteList::OnBnClickedBtnSetCustId()
{
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER);

	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 || nCurrSel>=(int)m_uCustomerNum)
	{
		MessageBox("请选择一条车主信息记录");
		return;
	}

	const VZ_LPR_WLIST_CUSTOMER &Cust = m_pLPCustomers[nCurrSel];
	SetDlgItemInt(IDC_EDIT_REC_LP_CUST_ID, Cust.uCustomerID);

	pList->SetFocus();
}

void DlgConfigWhiteList::OnBnClickedBtnPrevPageLp()
{
	if(m_uCurrLPRecPageNum == 0)
		return;

	m_uCurrLPRecPageNum--;

	iQueryLPRecPage();
}

void DlgConfigWhiteList::OnBnClickedBtnNextPageLp()
{
	if(m_uLPRecNum < g_LPRecPageSize)
		return;
	
	m_uCurrLPRecPageNum++;

	iQueryLPRecPage();
}

void DlgConfigWhiteList::OnBnClickedBtnDelLp()
{
	CListCtrl *pList = (CListCtrl *)GetDlgItem(IDC_LIST_LP_REC);
	int nCurrSel = iListCtrlGetCurSel(pList);
	if(nCurrSel < 0 && nCurrSel >= (int)m_uLPRecNum)
	{
		return;
	}

	VzLPRClient_WhiteListDeleteVehicle(m_hLPRC, m_pLPRecords[nCurrSel].strPlateID);
	
	if(m_uLPRecNum == 1)
	{
		if(m_uCurrLPRecPageNum >= 1)
		{
			m_uCurrLPRecPageNum--;
		}
	}

	iQueryLPRecPage();
}

void DlgConfigWhiteList::OnBnClickedBtnLookupLp()
{
	m_uLPRecNum = 0;

	((CListCtrl *)GetDlgItem(IDC_LIST_LP_REC))->DeleteAllItems();

	iCleanLPRecEdit();

	char str[32];
	GetDlgItemText(IDC_EDIT_LOOKUP_LP, str, 32);

	VzLPRClient_WhiteListLoadVehicleByPlateID(m_hLPRC, str);
}

void DlgConfigWhiteList::OnBnClickedBtnLookupCust()
{
	m_uCustomerNum = 0;

	((CListCtrl *)GetDlgItem(IDC_LIST_CUSTOMER))->DeleteAllItems();

	iCleanCustomerEdit();

	char str[32];
	GetDlgItemText(IDC_EDIT_LOOKUP_CUST_NAME, str, 32);

	VzLPRClient_WhiteListLoadCustomersByName(m_hLPRC, str);
}
#endif