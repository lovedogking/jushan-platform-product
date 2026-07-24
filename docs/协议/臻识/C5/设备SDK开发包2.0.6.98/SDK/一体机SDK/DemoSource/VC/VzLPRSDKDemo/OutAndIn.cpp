// OutAndIn.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "OutAndIn.h"
#include "SpecialPlatesCfg.h"

// COutAndIn dialog

IMPLEMENT_DYNAMIC(COutAndIn, CDialog)

//***********************************************************
// 函数:      COutAndIn
// 全称:  	  COutAndIn::COutAndIn
// 属性:      public 
// 返回:      
// 说明:      默认构造函数，初始化数据
// 参数:      
//***********************************************************
COutAndIn::COutAndIn(CWnd* parent)
	: CDialog(IDD, parent)
{
	initData();
}


//***********************************************************
// 函数:      ~COutAndIn
// 全称:  	  COutAndIn::~COutAndIn
// 属性:      virtual public 
// 返回:      
// 说明:      析构函数，释放对话框
//***********************************************************
COutAndIn::~COutAndIn()
{
	for (DlgMap::iterator it = m_oDlgMap.begin(); it != m_oDlgMap.end(); ++it)
	{
		delete it->second;
	}
	m_oDlgMap.clear();
}

//***********************************************************
// 函数:      setHandle
// 全称:  	  COutAndIn::setHandle
// 属性:      public 
// 返回:      void
// 说明:      设置设备句柄
// 参数:      VzLPRClientHandle handle
//***********************************************************
void COutAndIn::setHandle(VzLPRClientHandle handle)
{
	m_hLPRC = handle;
	for (DlgMap::iterator it = m_oDlgMap.begin(); it != m_oDlgMap.end(); ++it)
		it->second->SetLPRHandle(handle);
}

void COutAndIn::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_TAB2, m_oTab);
}

//***********************************************************
// 函数:      initData
// 全称:  	  COutAndIn::initData
// 属性:      protected 
// 返回:      void
// 说明:      初始化数据
//***********************************************************
void COutAndIn::initData()
{
	COutputConfig* pOutputConfig = new COutputConfig;
	m_oDlgMap.insert(make_pair(0, pOutputConfig));

	CSpecialPlatesCfg* pSpecialPlatesConfig = new CSpecialPlatesCfg;
	m_oDlgMap.insert(make_pair(1, pSpecialPlatesConfig));
	
	CTriggerDelay* pTriggerDelay = new CTriggerDelay;
	m_oDlgMap.insert(make_pair(2, pTriggerDelay));

	CWList* pWList = new CWList;
	m_oDlgMap.insert(make_pair(3, pWList));
}

BOOL COutAndIn::OnInitDialog()
{
	CDialog::OnInitDialog();
		
	//初始化子窗体
	m_oTab.InsertItem(0,"输出配置");
	m_oDlgMap[0]->Create(m_oDlgMap[0]->IDD(), &m_oTab);

	m_oTab.InsertItem(1,"特殊车牌控制");
	m_oDlgMap[1]->Create(m_oDlgMap[1]->IDD(), &m_oTab);

	m_oTab.InsertItem(2,"爆闪控制");
	m_oDlgMap[2]->Create(m_oDlgMap[2]->IDD(), &m_oTab);

	m_oTab.InsertItem(3,"白名单");
	m_oDlgMap[3]->Create(m_oDlgMap[3]->IDD(), &m_oTab);

	//子窗体位置
	CRect rc;
	m_oTab.GetClientRect(&rc);
	m_oTab.AdjustRect(FALSE, &rc);
	for(DlgMap::iterator it = m_oDlgMap.begin()
			; it != m_oDlgMap.end(); ++it)
	{
		it->second->MoveWindow(rc,0);
	}

	m_curSel = 0;
	m_oDlgMap[m_curSel]->ShowWindow(SW_SHOW);
	m_oDlgMap[m_curSel]->refresh();

	return TRUE; 
}

BEGIN_MESSAGE_MAP(COutAndIn, CDialog)
	ON_NOTIFY(TCN_SELCHANGE, IDC_TAB2, &COutAndIn::OnTcnSelchangeTab2)
END_MESSAGE_MAP()


// COutAndIn message handlers

//***********************************************************
// 函数:      OnTcnSelchangeTab2
// 全称:  	  COutAndIn::OnTcnSelchangeTab2
// 属性:      public 
// 返回:      void
// 说明:      tab选择事件
// 参数:      NMHDR * pNMHDR
// 参数:      LRESULT * pResult
//***********************************************************
void COutAndIn::OnTcnSelchangeTab2(NMHDR *pNMHDR, LRESULT *pResult)
{
	//旧窗体隐藏
	CDlgOutputAndInBase* pDlgBase = m_oDlgMap[m_curSel];
	if (pDlgBase == NULL)
		return;
	pDlgBase->ShowWindow(SW_HIDE);
	
	//新窗体刷新显示
	m_curSel = m_oTab.GetCurSel();
	pDlgBase = m_oDlgMap[m_curSel];
	if(pDlgBase != NULL)
	{
		pDlgBase->refresh();
		pDlgBase->ShowWindow(SW_SHOW);
	}
	*pResult = 0;
}
