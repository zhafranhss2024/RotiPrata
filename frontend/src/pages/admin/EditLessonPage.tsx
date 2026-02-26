import React from "react";
import { useParams } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { AdminLessonWizard } from "@/features/admin/wizard/AdminLessonWizard";

const EditLessonPage = () => {
  const { id } = useParams<{ id: string }>();

  return (
    <MainLayout>
      <div className="container mx-auto max-w-5xl px-4 py-6 md:py-8 pb-safe">
        <AdminLessonWizard mode="edit" lessonId={id} />
      </div>
    </MainLayout>
  );
};

export default EditLessonPage;
