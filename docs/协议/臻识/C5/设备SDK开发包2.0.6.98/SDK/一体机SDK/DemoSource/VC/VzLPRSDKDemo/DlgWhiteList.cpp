// DlgWhiteList.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgWhiteList.h"


// DlgWhiteList dialog

IMPLEMENT_DYNAMIC(DlgWhiteList, CDialog)

DlgWhiteList::DlgWhiteList(CWnd* pParent /*=NULL*/)
	: CDialog(DlgWhiteList::IDD, pParent)
{

}

void __stdcall CALLBACK_ON_QUERY_LP_RECORD(VZLPRC_WLIST_CB_TYPE type,const VZ_LPR_WLIST_VEHICLE *pLP, 
										   const VZ_LPR_WLIST_CUSTOMER *pCustomer,
										   void *pUserData)
{
	if(type == VZLPRC_WLIST_CB_TYPE_ROW){
		DlgWhiteList *pInstance = (DlgWhiteList *)pUserData;
		pInstance->m_dlgSubWhiteList2.iOnQueryLPRecord(type,pLP, pCustomer);
	}
	else{
		DlgWhiteList *pInstance = (DlgWhiteList *)pUserData;
		pInstance->m_dlgSubWhiteList1.iOnQueryLPRecord(type,pLP, pCustomer);
	}	
}

void DlgWhiteList::SetHandle(VzLPRClientHandle handle){	
	m_hLPRC = handle;
	m_dlgSubWhiteList1.SetHandle(handle);
	m_dlgSubWhiteList2.SetHandle(handle);
	VzLPRClient_WhiteListSetQueryCallBack(m_hLPRC, CALLBACK_ON_QUERY_LP_RECORD, this);
}

DlgWhiteList::~DlgWhiteList()
{
}

void DlgWhiteList::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_TAB1, m_tabWlMain);
}


BEGIN_MESSAGE_MAP(DlgWhiteList, CDialog)
	ON_NOTIFY(TCN_SELCHANGE, IDC_TAB1, &DlgWhiteList::OnTcnSelchangeTab1)
END_MESSAGE_MAP()


// DlgWhiteList message handlers

void DlgWhiteList::OnTcnSelchangeTab1(NMHDR *pNMHDR, LRESULT *pResult)
{
	if(m_subDlgs[m_curSel]!=NULL){
		m_subDlgs[m_curSel]->ShowWindow(SW_HIDE);
	}
	m_curSel = m_tabWlMain.GetCurSel();
	if(m_subDlgs[m_curSel]!=NULL){
		if(m_curSel == 0){
			m_dlgSubWhiteList1.Refresh();
		}
		else if(m_curSel == 1){
			m_dlgSubWhiteList2.Refresh();
		}
		m_subDlgs[m_curSel]->ShowWindow(SW_SHOW);
	}
	*pResult = 0;
}

BOOL DlgWhiteList::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_tabWlMain.InsertItem(0,"单表查询");
	//m_tabWlMain.InsertItem(1,"复合查询");

	m_dlgSubWhiteList1.Create(m_dlgSubWhiteList1.IDD,&m_tabWlMain);
	//m_dlgSubWhiteList2.Create(m_dlgSubWhiteList2.IDD,&m_tabWlMain);

	m_subDlgs[0]=&m_dlgSubWhiteList1;
	//m_subDlgs[1]=&m_dlgSubWhiteList2;

	CRect rc;
	m_tabWlMain.GetClientRect(&rc);
	m_tabWlMain.AdjustRect(FALSE, &rc);
	for(int i=0;i<MAX_NUM_TAB_DLG;i++)
	{
		if(m_subDlgs[i])
			m_subDlgs[i]->MoveWindow(rc,0);
	}

	m_curSel = 0;
	m_subDlgs[m_curSel]->ShowWindow(SW_SHOW);
	m_dlgSubWhiteList1.Refresh();

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}


BOOL DlgWhiteList::PreTranslateMessage(MSG* pMsg)
{
	// TODO: Add your specialized code here and/or call the base class
	if (pMsg->message == WM_KEYDOWN)  
	{  
		switch(pMsg->wParam)  
		{  
			case VK_ESCAPE: //Esc按键事件  
				return true;  
			
			case VK_RETURN: //Enter按键事件  
				return true;  
			
			default:  
					break;  
		}  
	}  

	return CDialog::PreTranslateMessage(pMsg);
}
