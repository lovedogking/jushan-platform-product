#include "stdafx.h"
#include "_ivzDraw.h"

#define iSetPixel(image, pitch, x, y, value)	\
{											\
	(image+(y)*pitch)[x] = value;			\
}

void ivzLine(unsigned char *image, int width, int height,
			 int widthStep, int channel, 
			 int x11, int y11, int x22, int y22,
			 const unsigned char color[])
{
	int x1 = x11, y1 = y11, x2 = x22, y2 = y22;
	int p;
	int _2Dx, _2Dy, _2DxDy, _2DyDx;
	int x, y, xEnd, yEnd;
	int dx, dy;

	const unsigned int uValue4CN = channel==4 ? ((color[0]<<16)|(color[1]<<8)|color[2]) : 0;

	if((x1<0&&x2<0) || (y1<0&&y2<0)|| (x1>=width&&x2>=width) || (y1>=height&&y2>=height))
		return;
	//使端点在图像范围内
	if(x1<0)
	{
		y1 = y1-x1*(y2-y1)/(x2-x1);
		x1 = 0;
	}
	else if(x1>=width)
	{
		y1 = y1-(x1-width+1)*(y2-y1)/(x2-x1);
		x1 = width-1;
	}
	if(x2<0)
	{
		y2 = y2-x2*(y2-y1)/(x2-x1);
		x2 = 0;
	}
	else if(x2>=width)
	{
		y2 = y2-(x2-width+1)*(y2-y1)/(x2-x1);
		x2 = width-1;
	}
	
	if(y1<0)
	{
		if(y2-y1==0)return;
		x1 = x1-y1*(x2-x1)/(y2-y1);
		y1 = 0;
	}
	else if(y1>=height)
	{
		if(y2-y1==0)return;
		x1 = x1-(y1-height+1)*(x2-x1)/(y2-y1);
		y1 = height-1;
	}
	if(y2<0)
	{
		if(y2-y1==0)return;
		x2 = x2-y2*(x2-x1)/(y2-y1);
		y2 = 0;
	}
	else if(y2>=height)
	{
		if(y2-y1==0)return;
		x2 = x2-(y2-height+1)*(x2-x1)/(y2-y1);
		y2 = height-1;
	}

	dx = x1>x2 ? x1-x2 : x2-x1;
	dy = y1>y2 ? y1-y2 : y2-y1;
	if(dx==0)
	{
		x = x1;
		if(y1>y2)
		{
			y = y2;
			yEnd = y1;
		}
		else
		{
			y = y1;
			yEnd = y2;
		}
		if(channel==1)
		{
			while(y<=yEnd)
			{
				iSetPixel(image, widthStep, x, y, color[0]);
				y++;
			}
		}
		else if(channel == 3)
		{
			while(y<=yEnd)
			{
				iSetPixel(image, widthStep, x*3, y, color[0]);
				iSetPixel(image, widthStep, x*3+1, y, color[1]);
				iSetPixel(image, widthStep, x*3+2, y, color[2]);
				y++;
			}
		}
		else if(channel == 4)
		{
			while(y<=yEnd)
			{
				((unsigned int *)(image + y*widthStep))[x] = uValue4CN;
				y++;
			}
		}
	}
	else if(dy==0)
	{
		y = y1;
		if(x1>x2)
		{
			x = x2;
			xEnd = x1;
		}
		else
		{
			x = x1;
			xEnd = x2;
		}
		if(channel==1)
		{
			while(x<=xEnd)
			{
				iSetPixel(image, widthStep, x, y, color[0]);
				x++;
			}
		}
		else if(channel == 3)
		{
			while(x<=xEnd)
			{
				iSetPixel(image, widthStep, x*3, y, color[0]);
				iSetPixel(image, widthStep, x*3+1, y, color[1]);
				iSetPixel(image, widthStep, x*3+2, y, color[2]);
				x++;
			}
		}
		else if(channel == 4)
		{
			while(x<=xEnd)
			{
				((unsigned int *)(image + y*widthStep))[x] = uValue4CN;
				x++;
			}
		}
	}
	else if(dx==dy)
	{
		if(x1>x2)
		{
			x = x2;
			y = y2;
			xEnd = x1;
			yEnd = y1;
		}
		else
		{
			x = x1;
			y = y1;
			xEnd = x2;
			yEnd = y2;
		}
		if(y>yEnd)
		{
			if(channel==1)
			{
				while(x<=xEnd)
				{
					iSetPixel(image, widthStep, x, y, color[0]);
					x++;
					y--;
				}
			}
			else if(channel == 3)
			{
				while(x<=xEnd)
				{
					iSetPixel(image, widthStep, x*3, y, color[0]);
					iSetPixel(image, widthStep, x*3+1, y, color[1]);
					iSetPixel(image, widthStep, x*3+2, y, color[2]);
					x++;
					y--;
				}
			}
			else if(channel == 4)
			{
				while(x<=xEnd)
				{
					((unsigned int *)(image + y*widthStep))[x] = uValue4CN;
					x++;
					y--;
				}
			}
		}
		else
		{
			if(channel==1)
			{
				while(x<=xEnd)
				{
					iSetPixel(image, widthStep, x, y, color[0]);
					x++;
					y++;
				}
			}
			else if(channel==3)
			{
				while(x<=xEnd)
				{
					iSetPixel(image, widthStep, x*3, y, color[0]);
					iSetPixel(image, widthStep, x*3+1, y, color[1]);
					iSetPixel(image, widthStep, x*3+2, y, color[2]);
					x++;
					y++;
				}
			}
			else if(channel == 4)
			{
				while(x<=xEnd)
				{
					((unsigned int *)(image + y*widthStep))[x] = uValue4CN;
					x++;
					y++;
				}
			}
		}
	}
	else if(dx>dy)
	{
		p = 2*dy-dx;
		_2Dy = 2*dy;
		_2DyDx = 2*(dy-dx);
	
		if(x1>x2)
		{
			x = x2;
			y = y2;
			xEnd = x1;
			yEnd = y1;
		}
		else
		{
			x = x1;
			y = y1;
			xEnd = x2;
			yEnd = y2;
		}

		if(y>yEnd)
		{
			if(channel==1)
			{
				while(x<=xEnd)
				{
					iSetPixel(image, widthStep, x, y, color[0]);
					x++;
					if(p<0)
					{
						p += _2Dy;
					}
					else
					{
						y--;
						p += _2DyDx;
					}
				}
			}
			else if(channel==3)
			{
				while(x<=xEnd)
				{
					iSetPixel(image, widthStep, x*3, y, color[0]);
					iSetPixel(image, widthStep, x*3+1, y, color[1]);
					iSetPixel(image, widthStep, x*3+2, y, color[2]);
					x++;
					if(p<0)
					{
						p += _2Dy;
					}
					else
					{
						y--;
						p += _2DyDx;
					}
				}
			}
			else if(channel==4)
			{
				while(x<=xEnd)
				{
					((unsigned int *)(image + y*widthStep))[x] = uValue4CN;
					x++;
					if(p<0)
					{
						p += _2Dy;
					}
					else
					{
						y--;
						p += _2DyDx;
					}
				}
			}
		}
		else
		{
			if(channel==1)
			{
				while(x<=xEnd)
				{
					iSetPixel(image, widthStep, x, y, color[0]);
					x++;
					if(p<0)
					{
						p += _2Dy;
					}
					else
					{
						y++;
						p += _2DyDx;
					}
				}
			}
			else if(channel==3)
			{
				while(x<=xEnd)
				{
					iSetPixel(image, widthStep, x*3, y, color[0]);
					iSetPixel(image, widthStep, x*3+1, y, color[1]);
					iSetPixel(image, widthStep, x*3+2, y, color[2]);
					x++;
					if(p<0)
					{
						p += _2Dy;
					}
					else
					{
						y++;
						p += _2DyDx;
					}
				}
			}
			else if(channel==4)
			{
				while(x<=xEnd)
				{
					((unsigned int *)(image + y*widthStep))[x] = uValue4CN;
					x++;
					if(p<0)
					{
						p += _2Dy;
					}
					else
					{
						y++;
						p += _2DyDx;
					}
				}
			}
		}
	}
	else if(dx<dy)
	{
		p = 2*dx-dy;
		_2Dx = 2*dx;
		_2DxDy = 2*(dx-dy);

		if(y1>y2)
		{
			x = x2;
			y = y2;
			xEnd = x1;
			yEnd = y1;
		}
		else
		{
			x = x1;
			y = y1;
			xEnd = x2;
			yEnd = y2;
		}

		if(x>xEnd)
		{
			if(channel==1)
			{
				while(y<=yEnd)
				{
					iSetPixel(image, widthStep, x, y, color[0]);
					y++;
					if(p<0)
					{
						p += _2Dx;
					}
					else
					{
						x--;
						p += _2DxDy;
					}
				}
			}
			else if(channel==3)
			{
				while(y<=yEnd)
				{
					iSetPixel(image, widthStep, x*3, y, color[0]);
					iSetPixel(image, widthStep, x*3+1, y, color[1]);
					iSetPixel(image, widthStep, x*3+2, y, color[2]);
					y++;
					if(p<0)
					{
						p += _2Dx;
					}
					else
					{
						x--;
						p += _2DxDy;
					}
				}
			}
			else if(channel==4)
			{
				while(y<=yEnd)
				{
					((unsigned int *)(image + y*widthStep))[x] = uValue4CN;
					y++;
					if(p<0)
					{
						p += _2Dx;
					}
					else
					{
						x--;
						p += _2DxDy;
					}
				}
			}
		}
		else
		{
			if(channel==1)
			{
				while(y<=yEnd)
				{
					iSetPixel(image, widthStep, x, y, color[0]);
					y++;
					if(p<0)
					{
						p += _2Dx;
					}
					else
					{
						x++;
						p += _2DxDy;
					}
				}
			}
			else if(channel==3)
			{
				while(y<=yEnd)
				{
					iSetPixel(image, widthStep, x*3, y, color[0]);
					iSetPixel(image, widthStep, x*3+1, y, color[1]);
					iSetPixel(image, widthStep, x*3+2, y, color[2]);
					y++;
					if(p<0)
					{
						p += _2Dx;
					}
					else
					{
						x++;
						p += _2DxDy;
					}
				}
			}
			else if(channel==4)
			{
				while(y<=yEnd)
				{
					((unsigned int *)(image + y*widthStep))[x] = uValue4CN;
					y++;
					if(p<0)
					{
						p += _2Dx;
					}
					else
					{
						x++;
						p += _2DxDy;
					}
				}
			}
		}
	}
}

int iFastSqrt(int x)
{
	int diff;
	int wordLength = 0;
	int y = x;
	int iter;
	int a;
	int i;

	while(y>0)
	{
		y = y>>1;
		wordLength++;
	}
	iter = wordLength>>1;
	a = y = 1<<iter;			//a is the first estimation value for sqrt(x)
	for(i=0; i<iter; i++ ) 	//each iteration achieves one bit of accuracy
	{
		a = a>>1;
		diff = x - y*y;
		if (diff > 0)
		{
			y = y + a;
		}
		else if (diff < 0)
		{
			y = y - a;
		}
	}
	return y;
}

static unsigned char iColor2Gray(const unsigned char Color[])
{
	double dGray = 0.257*Color[0] + 0.504*Color[1] + 0.098*Color[2] + 16;

	return((unsigned char)(dGray > 255 ? 255 : (dGray < 0 ? 0 : dGray)));
}

void ivzArrowRGB(unsigned char *pRGB, int w, int h, int p,
				  int xHead, int yHead, int xTail, int yTail,
				  int arrowHeight,	//箭头三角形的高
				  int arrowNum,		//箭头向下叠加绘制个数（包括当前）
				  int minArrowGap,	//多个箭头叠加时之间的最小间隔
				  int bNeedLine,	//绘制出箭头到箭尾的线段
				  const unsigned char color[],
				  unsigned char bColor)
{
	const int DX = xHead - xTail;
	const int DY = yHead - yTail;
	const int SegLen = iFastSqrt(DX*DX + DY*DY);
	const float _SegLen = 1.0f/SegLen;
	int arrowStep;
	float RateDX, RateDY, ArrowRootX, ArrowRootY, RateStepX, RateStepY;
	int i;

	const unsigned char *pColor = color;
	unsigned char iColor[3];
	if(bColor == 0)
	{
		iColor[0] = iColor[1] = iColor[2] = iColor2Gray(pColor);
		pColor = iColor;
	}

	arrowHeight = SegLen < arrowHeight ? SegLen : arrowHeight;

	arrowStep = (SegLen-arrowHeight)/arrowNum;
	
	//箭头间距有下限
	if(arrowStep < minArrowGap)
	{
		arrowStep = minArrowGap;
		arrowNum = (SegLen - arrowHeight + arrowStep - 1)/arrowStep;
	}

	if(bNeedLine)
	{
		ivzLine(pRGB, w, h, p, 3, xHead, yHead, xTail, yTail, pColor);
	}

	RateDX = (DX * arrowHeight*_SegLen);
	RateDY = (DY * arrowHeight*_SegLen);
	ArrowRootX = xHead - RateDX;
	ArrowRootY = yHead - RateDY;
	RateStepX = DX * arrowStep*_SegLen;
	RateStepY = DY * arrowStep*_SegLen;

	for(i=0; i<arrowNum; i++)
	{
		const float StepX = (i*RateStepX);
		const float StepY = (i*RateStepY);
		int arrowX = (int)(ArrowRootX + RateDY - StepX + 0.5f);
		int arrowY = (int)(ArrowRootY - RateDX - StepY + 0.5f);

		const int xStart = xHead - (int)StepX;
		const int yStart = yHead - (int)StepY;

		ivzLine(pRGB, w, h, p, 3, xStart, yStart, arrowX, arrowY, pColor);

		arrowX = (int)(ArrowRootX - RateDY - StepX + 0.5f);
		arrowY = (int)(ArrowRootY + RateDX - StepY + 0.5f);

		ivzLine(pRGB, w, h, p, 3, xStart, yStart, arrowX, arrowY, pColor);
	}
}
