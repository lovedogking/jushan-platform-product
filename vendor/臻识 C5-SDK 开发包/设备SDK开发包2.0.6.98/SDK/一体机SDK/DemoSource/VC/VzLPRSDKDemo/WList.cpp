#include "stdafx.h"
#include "WList.h"
#include "VzLPRClientSDK.h"
#include <VzLPRFaceClientSDK.h>
// CWList dialog

IMPLEMENT_DYNAMIC(CWList, CDialog)

CWList::CWList()
	: CDlgOutputAndInBase()
	, m_nWLEnable(-1)
	, m_nFuzzy(-1)
	, m_nFuzzyLen(1)
	, m_bFuzzyCC(FALSE)
{
}

CWList::~CWList()
{
}

void CWList::refresh()
{
	VzLPRClient_GetWLCheckMethod(m_hLPRClient, &m_nWLEnable);
	bool bFuzzyCC;
	VzLPRClient_GetWLFuzzy(m_hLPRClient, &m_nFuzzy, &m_nFuzzyLen, &bFuzzyCC);
	--m_nFuzzyLen;
	m_bFuzzyCC = bFuzzyCC ? 1 : 0;

	if( m_nFuzzy != 2 )
	{
		SetWindowEnable(FALSE);
	}

	UpdateData(false);
}

void CWList::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Radio(pDX, IDC_RADIO_Auto, m_nWLEnable);
	DDX_Radio(pDX, IDC_RADIO_Auto2, m_nFuzzy);
	DDX_Radio(pDX, IDC_RADIO2, m_nFuzzyLen);
	DDX_Check(pDX, IDC_CHECK2, m_bFuzzyCC);
}


BEGIN_MESSAGE_MAP(CWList, CDialog)
	ON_BN_CLICKED(IDC_BTN_WLEnable, &CWList::OnBnClickedBtnWlenable)
	ON_BN_CLICKED(IDC_BTN_Fuzzy, &CWList::OnBnClickedBtnFuzzy)
	ON_BN_CLICKED(IDC_RADIO_Auto2, &CWList::OnBnClickedRadioAuto2)
	ON_BN_CLICKED(IDC_RADIO_Enable2, &CWList::OnBnClickedRadioEnable2)
	ON_BN_CLICKED(IDC_RADIO_Disable2, &CWList::OnBnClickedRadioDisable2)
END_MESSAGE_MAP()


// CWList message handlers

void CWList::OnBnClickedBtnWlenable()
{
	UpdateData(true);
	int nRet = VzLPRClient_SetWLCheckMethod(m_hLPRClient, m_nWLEnable);
	if (nRet != SUCCESS)
		MessageBox("设置启用条件失败！", "白名单启用", MB_OK);
	else
		MessageBox("设置启用条件成功！", "白名单启用", MB_OK);
}

void CWList::OnBnClickedBtnFuzzy()
{
	UpdateData(true);
	bool bFuzzyCC = (m_bFuzzyCC == 1);
	int nRet = VzLPRClient_SetWLFuzzy(m_hLPRClient, m_nFuzzy, m_nFuzzyLen + 1, m_bFuzzyCC);
	if (nRet != SUCCESS) {
		MessageBox("设置模糊查询方式失败！", "白名单模糊匹配", MB_OK);
	}
	else {
		MessageBox("设置模糊查询方式成功！", "白名单模糊匹配", MB_OK);
	}
}

void CWList::OnBnClickedRadioAuto2()
{
	SetWindowEnable(FALSE);
}

void CWList::OnBnClickedRadioEnable2()
{
	SetWindowEnable(FALSE);
}

void CWList::OnBnClickedRadioDisable2()
{
	SetWindowEnable(TRUE);
}

void CWList::SetWindowEnable(BOOL enable)
{
	GetDlgItem(IDC_CHECK2)->EnableWindow(enable);
	GetDlgItem(IDC_RADIO2)->EnableWindow(enable);
	GetDlgItem(IDC_RADIO3)->EnableWindow(enable);
	GetDlgItem(IDC_RADIO4)->EnableWindow(enable);
}