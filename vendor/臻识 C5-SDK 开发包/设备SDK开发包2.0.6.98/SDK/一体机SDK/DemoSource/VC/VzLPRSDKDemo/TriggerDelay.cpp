// TriggerDelay.cpp : implementation file
//

#include "stdafx.h"
#include "TriggerDelay.h"
#include "VzClientSDK_CommonDefine.h"
#include "VzLPRClientSDK.h"
#include <VzLPRFaceClientSDK.h>

// CTriggerDelay dialog

IMPLEMENT_DYNAMIC(CTriggerDelay, CDialog)

CTriggerDelay::CTriggerDelay()
	: CDlgOutputAndInBase()
	,m_nTriDelay(0)
{
	
}

CTriggerDelay::~CTriggerDelay()
{
}

void CTriggerDelay::refresh()
{
    int nRet = VzLPRClient_GetLedComplensating(m_hLPRClient,&m_bCompEnable,&m_nTriDelay,&m_nBlastLast,&m_nBlastDelay,m_szGpioIn,3,m_szGpioOut,6);
    if (nRet != SUCCESS)
    {
		return;
		UpdateData(false);
    }
	else
	{
		sprintf_s(m_sTriDelay, 7, "%d", m_nTriDelay);
		sprintf_s(m_sBlastLast, 7, "%d", m_nBlastLast);
		sprintf_s(m_sBlastDelay, 7, "%d", m_nBlastDelay);

        SetDlgItemText(IDC_EDIT_TRIDELAY, m_sTriDelay);
        SetDlgItemText(IDC_EDIT_BlastLast, m_sBlastLast);
        SetDlgItemText(IDC_EDIT_BlastDelay, m_sBlastDelay);

		((CButton *)GetDlgItem(IDC_CHKISOPEN))->SetCheck(m_bCompEnable == true? BST_CHECKED : BST_UNCHECKED);
        ((CButton *)GetDlgItem(IDC_CHK_IO1))->SetCheck(m_szGpioIn[0] == 1? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_IO2))->SetCheck(m_szGpioIn[1] == 1? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_IO3))->SetCheck(m_szGpioIn[2] == 1? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_IOOUT1))->SetCheck(m_szGpioOut[0] == 1? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_IOOUT2))->SetCheck(m_szGpioOut[1] == 1? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_IOOUT3))->SetCheck(m_szGpioOut[2] == 1? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_IOOUT4))->SetCheck(m_szGpioOut[3] == 1? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_TTLOUT1))->SetCheck(m_szGpioOut[4] == 1? BST_CHECKED : BST_UNCHECKED);
		((CButton *)GetDlgItem(IDC_CHK_TTLOUT2))->SetCheck(m_szGpioOut[5] == 1? BST_CHECKED : BST_UNCHECKED);
	}

	
}

void CTriggerDelay::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
}

BEGIN_MESSAGE_MAP(CTriggerDelay, CDialog)
	ON_BN_CLICKED(IDOK, &CTriggerDelay::OnBnClickedOk)
END_MESSAGE_MAP()


// CTriggerDelay message handlers

void CTriggerDelay::OnBnClickedOk()
{
	UpdateData(true);
	bool bCompEnable;
	bCompEnable  = ((CButton *)GetDlgItem(IDC_CHKISOPEN))->GetCheck() == BST_CHECKED ? true : false;  
	m_szGpioIn[0]  = ((CButton *)GetDlgItem(IDC_CHK_IO1))->GetCheck() == BST_CHECKED ? 1 : 0;
	m_szGpioIn[1]  = ((CButton *)GetDlgItem(IDC_CHK_IO2))->GetCheck() == BST_CHECKED ? 1 : 0;
	m_szGpioIn[2]  = ((CButton *)GetDlgItem(IDC_CHK_IO3))->GetCheck() == BST_CHECKED ? 1 : 0;
	m_szGpioOut[0] = ((CButton *)GetDlgItem(IDC_CHK_IOOUT1))->GetCheck() == BST_CHECKED ? 1 : 0;
	m_szGpioOut[1] = ((CButton *)GetDlgItem(IDC_CHK_IOOUT2))->GetCheck() == BST_CHECKED ? 1 : 0;
	m_szGpioOut[2] = ((CButton *)GetDlgItem(IDC_CHK_IOOUT3))->GetCheck() == BST_CHECKED ? 1 : 0;
	m_szGpioOut[3] = ((CButton *)GetDlgItem(IDC_CHK_IOOUT4))->GetCheck() == BST_CHECKED ? 1 : 0;
	m_szGpioOut[4] = ((CButton *)GetDlgItem(IDC_CHK_TTLOUT1))->GetCheck() == BST_CHECKED ? 1 : 0;
	m_szGpioOut[5] = ((CButton *)GetDlgItem(IDC_CHK_TTLOUT2))->GetCheck() == BST_CHECKED ? 1 : 0;

	GetDlgItemText(IDC_EDIT_TRIDELAY, m_sTriDelay, 8);
	GetDlgItemText(IDC_EDIT_BlastLast, m_sBlastLast, 8);
	GetDlgItemText(IDC_EDIT_BlastDelay, m_sBlastDelay, 8);

	m_nTriDelay = atoi(m_sTriDelay);
	m_nBlastLast = atoi(m_sBlastLast);
	m_nBlastDelay = atoi(m_sBlastDelay);

	int nRet = VzLPRClient_SetLedComplensating(m_hLPRClient, bCompEnable, m_nTriDelay, m_nBlastLast, m_nBlastDelay, m_szGpioIn,3, m_szGpioOut,6);
	if (nRet != SUCCESS)
		MessageBox("ÉèÖÃ±¬ÉÁ¿ØÖÆÊ§°Ü£¡");
	else
		MessageBox("ÉèÖÃ±¬ÉÁ¿ØÖÆ³É¹¦£¡");
}

BOOL CTriggerDelay::OnInitDialog()
{
	CDlgOutputAndInBase::OnInitDialog();

	// TODO:  Add extra initialization here
	int board_type = 0;
	int ret_board = VzLPRClient_GetHwBoardType(m_hLPRClient, &board_type);
	if( board_type == 3 )
	{
		GetDlgItem(IDC_CHK_IOOUT3)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHK_IOOUT4)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHK_TTLOUT1)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHK_TTLOUT2)->ShowWindow(SW_HIDE);
		GetDlgItem(IDC_CHK_IO3)->ShowWindow(SW_HIDE);
	}

	int ver1 = 0, ver2 = 0, ver3 = 0,ver4 = 0;
	VzLPRClient_GetRemoteSoftWareVersion(m_hLPRClient, &ver1, &ver2, &ver3, &ver4);
	if(ver1 == 12) {
		GetDlgItem(IDOK)->EnableWindow(FALSE);
	}
	

	return TRUE;  // return TRUE unless you set the focus to a control
	// EXCEPTION: OCX Property Pages should return FALSE
}
