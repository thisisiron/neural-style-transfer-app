from django.http import HttpResponse
from django.views.decorators.csrf import csrf_exempt
from django.conf import settings
from django.shortcuts import render
import os
from PIL  import Image
import re

def index(request):
    return HttpResponse("Hello World")

@csrf_exempt
def upload_file(request):
    if 'pic' in request.FILES:
        ret = handle_uploaded_file(request.FILES['pic'])
        fname = re.findall(r'server/media/(.*).jpg', ret)[0]
        try:
            with open('./transfer/' + fname + '_transfer.jpg', "rb") as f:
                return HttpResponse(f.read(), content_type="image/jpeg")
        except IOError:
            red = Image.new('RGBA', (1, 1), (255,0,0,0))
            response = HttpResponse(content_type="image/jpeg")
            red.save(response, "JPEG")
            return response
        # return HttpResponse("Uploaded Image!" + "\n" + ret)
    return HttpResponse("No Image Received!")

def handle_uploaded_file(f):
    fname = os.path.join(os.getcwd(), 'media', f.name)
    destination = open(fname, 'wb+')
    for chunk in f.chunks(): 
        destination.write(chunk)
    destination.close()
    transfer(fname)
    return fname

def transfer(fname):
    os.system('python neural_style_transfer.py --image ' + fname + ' --model models/eccv16/starry_night.t7')