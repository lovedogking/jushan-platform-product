#pragma once


// VzTreeCtrl
#define ITEM_ON		(0x1<<32)
#define ITEM_OFF	(0x0<<32)

class VzTreeCtrl : public CTreeCtrl
{
	DECLARE_DYNAMIC(VzTreeCtrl)

public:
	VzTreeCtrl();
	virtual ~VzTreeCtrl();

protected:
	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnPaint();
	afx_msg void OnNMDblclk(NMHDR *pNMHDR, LRESULT *pResult);
};


